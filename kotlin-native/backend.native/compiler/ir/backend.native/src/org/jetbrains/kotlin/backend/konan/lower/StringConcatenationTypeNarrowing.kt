/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.konan.lower

import org.jetbrains.kotlin.backend.konan.Context
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.lower.IrBuildingTransformer
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.util.isTrivial
import org.jetbrains.kotlin.ir.util.shallowCopy
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.Name

/**
 * This pass replaces calls to:
 * - StringBuilder.append(Any?) with StringBuilder.append(String?)
 * - StringBuilder.append(Any) with StringBuilder.append(String)
 * - String.plus(Any?) with String.plusImpl(String)
 * - String?.plus(Any?) with String.plusImpl(String)
 * For this, toString() is called for non-String arguments. This call can be later devirtualized, improving escape analysis
 * For nullable arguments, the following snippet is used:
 * - "if (arg==null) null else arg.toString()"  to pass to StringBuilder.append(String?)
 * - "if (arg==null) "null" else arg.toString()"  to pass to other methods as non-nullable String
 */
internal class StringConcatenationTypeNarrowing(val context: Context) : FileLoweringPass, IrBuildingTransformer(context) {

    private val string = context.ir.symbols.string.owner
    private val stringBuilder = context.ir.symbols.stringBuilder.owner
    private val namePlusImpl = Name.identifier("plusImpl")
    private val nameAppend = Name.identifier("append")

    private val appendStringFunction = stringBuilder.functions.single {  // StringBuilder.append(String)
        it.name == nameAppend &&
                it.valueParameters.singleOrNull()?.type?.isString() == true
    }
    private val appendNullableStringFunction = stringBuilder.functions.single {  // StringBuilder.append(String?)
        it.name == nameAppend &&
                it.valueParameters.singleOrNull()?.type?.isNullableString() == true
    }
    private val appendAnyFunction = stringBuilder.functions.single {  // StringBuilder.append(Any?)
        it.name == nameAppend &&
                it.valueParameters.singleOrNull()?.type?.isNullableAny() == true
    }

    // null happens in :kotlin-native:endorsedLibraries:kotlinx.cli:macos_arm64KotlinxCliCache
    private val plusImplFunction = string.functions.singleOrNull {// external fun String.plusImpl(String)
        it.name == namePlusImpl &&
                it.valueParameters.size == 1 &&
                it.valueParameters.single().type.isString()
    }

    override fun lower(irFile: IrFile) {
        irFile.transformChildrenVoid(this)
    }

    override fun visitCall(expression: IrCall): IrExpression {
        expression.transformChildrenVoid(this)
        return with(expression) {
            when (symbol) {
                appendAnyFunction.symbol -> {  // StringBuilder.append(Any?)
                    val argument = getValueArgument(0)!!
                    if (argument.type.isNullable()) {
                        // Transform `StringBuilder.append(Any?)` to `StringBuilder.append(ARG?.toString())`, using "StringBuilder.append(String?)"
                        buildConcatenationCall(appendNullableStringFunction, dispatchReceiver!!, argument, ::buildNullableArgToNullableString)
                    } else {
                        // Transform `StringBuilder.append(Any)` to `StringBuilder.append(ARG.toString())`, using "StringBuilder.append(String)"
                        buildConcatenationCall(appendStringFunction, dispatchReceiver!!, argument, ::buildNonNullableArgToString)
                    }
                }

                context.irBuiltIns.memberStringPlus -> plusImplFunction?.let { // String.plus(Any?)
                    buildConcatenationCall(it, dispatchReceiver!!, getValueArgument(0)!!, ::buildNullableArgToString)
                } ?: expression

                context.irBuiltIns.extensionStringPlus -> plusImplFunction?.let {   // String?.plus(Any?)
                    buildConcatenationCall(it, buildNullableArgToString(extensionReceiver!!), getValueArgument(0)!!, ::buildNullableArgToString)
                } ?: expression

                else -> expression
            }
        }
    }

    private fun buildConcatenationCall(function: IrSimpleFunction, receiver: IrExpression, argument: IrExpression,
                                       blockBuilder: (IrExpression) -> IrExpression) =
            builder.irCall(function.symbol, function.returnType, valueArgumentsCount = 1, typeArgumentsCount = 0).apply {
                putValueArgument(0, blockBuilder(argument))
                dispatchReceiver = receiver
            }

    private fun buildEQNull(arg0: IrExpression) =
            builder.irCall(context.irBuiltIns.eqeqSymbol, context.irBuiltIns.booleanType, valueArgumentsCount = 2, typeArgumentsCount = 0).apply {
                putValueArgument(0, arg0)
                putValueArgument(1, IrConstImpl.constNull(startOffset, endOffset, context.irBuiltIns.nothingNType))
            }

    // Builds snippet of type String
    // - "if(argument==null) "null" else argument.toString()", if argument's type is nullable. Note: fortunately, all "null" string structures are unified
    // - "argument.toString()", otherwise
    private fun buildNullableArgToString(argument: IrExpression): IrExpression {
        return if (argument.type.isNullable()) {
            builder.irBlock {
                val argumentValue = createTempValIfNontrivial(argument)
                +irIfThenElse(
                        context.irBuiltIns.stringType,
                        condition = buildEQNull(argumentValue),
                        thenPart = IrConstImpl.string(startOffset, endOffset, context.irBuiltIns.stringType, "null"),
                        elsePart = buildNonNullableArgToString(argumentValue.shallowCopy()),
                        origin = null
                )
            }
        } else buildNonNullableArgToString(argument)
    }

    // Builds snippet of type String?
    // "if(argument==null) null else argument.toString()", that is similar to "argument?.toString()"
    private fun buildNullableArgToNullableString(argument: IrExpression): IrExpression {
        return builder.irBlock {
            val argumentValue = createTempValIfNontrivial(argument)
            +irIfThenElse(
                    context.irBuiltIns.stringType.makeNullable(),
                    condition = buildEQNull(argumentValue),
                    thenPart = IrConstImpl.constNull(startOffset, endOffset, context.irBuiltIns.nothingNType),
                    elsePart = buildNonNullableArgToString(argumentValue.shallowCopy()),
                    origin = null
            )
        }
    }

    // Builds snippet of type String
    // - "argument", in case argument's type is String, since String.toString() is no-op
    // - "argument", in case argument's type is String?, due to smart-cast and no-op
    // - "argument.toString()", otherwise
    private fun buildNonNullableArgToString(argument: IrExpression): IrExpression {
        return if (argument.type.isString() || argument.type.isNullableString())
            argument
        else builder.irCall(context.ir.symbols.memberToString, context.irBuiltIns.stringType, valueArgumentsCount = 1, typeArgumentsCount = 0).apply {
            dispatchReceiver = argument
        }
    }

    /**
     * If [expression] is non-trivial, this function creates a temporary local variable for that expression and returns [IrGetValue] for it.
     * Otherwise, it returns original trivial [expression]. This helps reduce excessive unnecessary local variable usage.
     */
    private fun IrBlockBuilder.createTempValIfNontrivial(expression: IrExpression): IrExpression =
            if (expression.isTrivial())
                expression
            else irGet(createTmpVariable(expression))
}
