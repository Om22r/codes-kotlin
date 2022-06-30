/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package kotlin.native.internal.reflect

import kotlin.native.internal.GCUnsafeCall
import kotlin.native.internal.InternalForKotlinNative

@GCUnsafeCall("Kotlin_internal_reflect_getObjectReferenceFieldsCount")
private external fun getObjectReferenceFieldsCount(o: Any): Int
@GCUnsafeCall("Kotlin_internal_reflect_getObjectReferenceFieldByIndex")
private external fun getObjectReferenceFieldByIndex(o: Any, index: Int): Any?

/**
 * The function returns [List] of non-primitive fields of an object.
 *
 * This function is intended to be used for tests and debugging only.
 * Function heavily relies on internal abi details. No compatibility guarantees on exact list content are provided.
 * Order, exact list of included fields, and representation of a field are subject to change.
 *
 * Limitations:
 *   - Primitives (unboxed [Int], [Double], [Float], etc) are not included in the list.
 *   - Inline classes over primitives would be not included in the list, if they are stored unboxed.
 *   - Inline classes would be stored in the list as their only field, if they are stored unboxed. There is no way to find the original type.
 *   - Synthetic fields (e.g. special fields for delegation) are included in the list.
 *   - There is no way to find which object in the list corresponds to which field.
 *
 * Special cases:
 *   - For `Array<T>` list of all it's elements would be returned
 */
@InternalForKotlinNative
fun Any.getObjectReferenceFields() : List<Any> {
    return when {
        this is Array<*> -> this.filterNotNull()
        else -> {
            buildList {
                for (index in 0 until getObjectReferenceFieldsCount(this@getObjectReferenceFields)) {
                    getObjectReferenceFieldByIndex(this@getObjectReferenceFields, index)?.let {
                        add(it)
                    }
                }
            }
        }
    }
}