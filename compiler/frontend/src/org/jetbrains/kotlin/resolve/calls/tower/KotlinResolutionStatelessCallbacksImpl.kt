/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.resolve.calls.tower

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.config.AnalysisFlags
import org.jetbrains.kotlin.config.LanguageVersionSettings
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.calls.callResolverUtil.isConventionCall
import org.jetbrains.kotlin.resolve.calls.callResolverUtil.isInfixCall
import org.jetbrains.kotlin.resolve.calls.callResolverUtil.isSuperOrDelegatingConstructorCall
import org.jetbrains.kotlin.resolve.calls.components.KotlinResolutionCallbacks
import org.jetbrains.kotlin.resolve.calls.components.KotlinResolutionStatelessCallbacks
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemBuilderImpl
import org.jetbrains.kotlin.resolve.calls.inference.components.ConstraintInjector
import org.jetbrains.kotlin.resolve.calls.inference.components.SimpleConstraintSystemImpl
import org.jetbrains.kotlin.resolve.calls.inference.isBuilderInferenceCall
import org.jetbrains.kotlin.resolve.calls.model.CallableReferenceKotlinCallArgument
import org.jetbrains.kotlin.resolve.calls.model.KotlinCall
import org.jetbrains.kotlin.resolve.calls.model.KotlinCallArgument
import org.jetbrains.kotlin.resolve.calls.model.SimpleKotlinCallArgument
import org.jetbrains.kotlin.resolve.calls.results.SimpleConstraintSystem
import org.jetbrains.kotlin.resolve.deprecation.DeprecationResolver
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeRefinerImpl
import org.jetbrains.kotlin.types.TypeIntersector
import org.jetbrains.kotlin.types.checker.KotlinTypeRefiner
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class KotlinResolutionStatelessCallbacksImpl(
    private val deprecationResolver: DeprecationResolver,
    private val languageVersionSettings: LanguageVersionSettings,
    private val kotlinTypeRefiner: KotlinTypeRefiner
) : KotlinResolutionStatelessCallbacks {
    override fun isDescriptorFromSource(descriptor: CallableDescriptor) =
        DescriptorToSourceUtils.descriptorToDeclaration(descriptor) != null

    override fun isInfixCall(kotlinCall: KotlinCall) =
        kotlinCall is PSIKotlinCallImpl && isInfixCall(kotlinCall.psiCall)

    override fun isOperatorCall(kotlinCall: KotlinCall) =
        (kotlinCall is PSIKotlinCallForInvoke) ||
                (kotlinCall is PSIKotlinCallImpl && isConventionCall(kotlinCall.psiCall))

    override fun isSuperOrDelegatingConstructorCall(kotlinCall: KotlinCall) =
        kotlinCall is PSIKotlinCallImpl && isSuperOrDelegatingConstructorCall(kotlinCall.psiCall)

    override fun isHiddenInResolution(
        descriptor: DeclarationDescriptor, kotlinCall: KotlinCall, resolutionCallbacks: KotlinResolutionCallbacks,
    ) =
        deprecationResolver.isHiddenInResolution(
            descriptor,
            (kotlinCall as? PSIKotlinCall)?.psiCall,
            (resolutionCallbacks as? KotlinResolutionCallbacksImpl)?.trace?.bindingContext,
            kotlinCall is PSIKotlinCallImpl && kotlinCall.psiCall.isCallWithSuperReceiver(),
        )

    override fun isHiddenInResolution(
        descriptor: DeclarationDescriptor,
        kotlinCallArgument: KotlinCallArgument,
        resolutionCallbacks: KotlinResolutionCallbacks
    ): Boolean =
        deprecationResolver.isHiddenInResolution(
            descriptor,
            kotlinCallArgument.psiCallArgument.psiExpression,
            (resolutionCallbacks as? KotlinResolutionCallbacksImpl)?.trace?.bindingContext,
            isSuperCall = false,
            fromImportingScope = false
        )

    override fun isSuperExpression(receiver: SimpleKotlinCallArgument?): Boolean =
        receiver?.psiExpression is KtSuperExpression

    override fun getScopeTowerForCallableReferenceArgument(argument: CallableReferenceKotlinCallArgument): ImplicitScopeTower =
        (argument as CallableReferenceKotlinCallArgumentImpl).scopeTowerForResolution

    override fun getVariableCandidateIfInvoke(functionCall: KotlinCall) =
        functionCall.safeAs<PSIKotlinCallForInvoke>()?.variableCall

    override fun isBuilderInferenceCall(argument: KotlinCallArgument, parameter: ValueParameterDescriptor): Boolean =
        isBuilderInferenceCall(parameter, argument.psiCallArgument.valueArgument, languageVersionSettings)

    override fun isApplicableCallForBuilderInference(
        descriptor: CallableDescriptor,
        languageVersionSettings: LanguageVersionSettings,
    ): Boolean {
        return org.jetbrains.kotlin.resolve.calls.inference.isApplicableCallForBuilderInference(descriptor, languageVersionSettings)
    }

    override fun isOldIntersectionIsEmpty(types: Collection<KotlinType>): Boolean {
        return TypeIntersector.intersectTypes(types) == null
    }

    override fun createConstraintSystemForOverloadResolution(
        constraintInjector: ConstraintInjector, builtIns: KotlinBuiltIns
    ): SimpleConstraintSystem {
        return if (languageVersionSettings.getFlag(AnalysisFlags.constraintSystemForOverloadResolution).forNewInference())
            SimpleConstraintSystemImpl(constraintInjector, builtIns, kotlinTypeRefiner)
        else
            ConstraintSystemBuilderImpl.forSpecificity()
    }
}
