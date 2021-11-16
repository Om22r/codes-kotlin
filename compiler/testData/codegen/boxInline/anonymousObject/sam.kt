// TARGET_BACKEND: JVM
// NO_CHECK_LAMBDA_INLINING
// FILE: 1.kt
package test

inline fun <reified T> makeRunnable(noinline lambda: ()->Unit) : Runnable {
    return Runnable(lambda)
}

// FILE: 2.kt

import test.*

fun box(): String {
    var result = "fail"

    makeRunnable<String> { result = "OK" }.run()

    return result
}
