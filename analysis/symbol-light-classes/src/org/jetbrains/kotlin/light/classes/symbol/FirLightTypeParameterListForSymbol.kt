/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.light.classes.symbol

import com.intellij.psi.*
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.kotlin.asJava.classes.lazyPub
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithTypeParameters
import org.jetbrains.kotlin.light.classes.symbol.elements.FirLightTypeParameter

import org.jetbrains.kotlin.analysis.api.KtAnalysisSession

context(KtAnalysisSession)
internal class FirLightTypeParameterListForSymbol(
    internal val owner: PsiTypeParameterListOwner,
    private val symbolWithTypeParameterList: KtSymbolWithTypeParameters,
) : LightElement(owner.manager, KotlinLanguage.INSTANCE), PsiTypeParameterList {

    override fun accept(visitor: PsiElementVisitor) {
        if (visitor is JavaElementVisitor) {
            visitor.visitTypeParameterList(this)
        } else {
            visitor.visitElement(this)
        }
    }

    override fun processDeclarations(
        processor: PsiScopeProcessor,
        state: ResolveState,
        lastParent: PsiElement?,
        place: PsiElement
    ): Boolean {
        return typeParameters.all { processor.execute(it, state) }
    }

    private val _typeParameters: Array<PsiTypeParameter> by lazyPub {
        symbolWithTypeParameterList.typeParameters.mapIndexed { index, parameter ->
            FirLightTypeParameter(
                parent = this@FirLightTypeParameterListForSymbol,
                index = index,
                typeParameterSymbol = parameter
            )
        }.toTypedArray()
    }

    override fun getTypeParameters(): Array<PsiTypeParameter> = _typeParameters

    override fun getTypeParameterIndex(typeParameter: PsiTypeParameter?): Int =
        _typeParameters.indexOf(typeParameter)

    override fun toString(): String = "FirLightTypeParameterList"

    override fun equals(other: Any?): Boolean =
        this === other ||
                (other is FirLightTypeParameterListForSymbol && symbolWithTypeParameterList == other.symbolWithTypeParameterList)

    override fun hashCode(): Int = symbolWithTypeParameterList.hashCode()

    override fun isEquivalentTo(another: PsiElement?): Boolean =
        basicIsEquivalentTo(this, another)

    override fun getParent(): PsiElement = owner
    override fun getContainingFile(): PsiFile = parent.containingFile
    override fun getText(): String? = ""
    override fun getTextOffset(): Int = 0
    override fun getStartOffsetInParent(): Int = 0
}
