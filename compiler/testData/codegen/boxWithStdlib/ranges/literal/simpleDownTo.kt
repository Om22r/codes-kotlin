// Auto-generated by org.jetbrains.kotlin.generators.tests.GenerateRangesCodegenTestData. DO NOT EDIT!
import java.util.ArrayList
import java.lang as j

fun box(): String {
    val list1 = ArrayList<Int>()
    for (i in 9 downTo 3) {
        list1.add(i)
        if (list1.size() > 23) break
    }
    if (list1 != listOf<Int>(9, 8, 7, 6, 5, 4, 3)) {
        return "Wrong elements for 9 downTo 3: $list1"
    }

    val list2 = ArrayList<Byte>()
    for (i in 9.toByte() downTo 3.toByte()) {
        list2.add(i)
        if (list2.size() > 23) break
    }
    if (list2 != listOf<Byte>(9, 8, 7, 6, 5, 4, 3)) {
        return "Wrong elements for 9.toByte() downTo 3.toByte(): $list2"
    }

    val list3 = ArrayList<Short>()
    for (i in 9.toShort() downTo 3.toShort()) {
        list3.add(i)
        if (list3.size() > 23) break
    }
    if (list3 != listOf<Short>(9, 8, 7, 6, 5, 4, 3)) {
        return "Wrong elements for 9.toShort() downTo 3.toShort(): $list3"
    }

    val list4 = ArrayList<Long>()
    for (i in 9.toLong() downTo 3.toLong()) {
        list4.add(i)
        if (list4.size() > 23) break
    }
    if (list4 != listOf<Long>(9, 8, 7, 6, 5, 4, 3)) {
        return "Wrong elements for 9.toLong() downTo 3.toLong(): $list4"
    }

    val list5 = ArrayList<Char>()
    for (i in 'g' downTo 'c') {
        list5.add(i)
        if (list5.size() > 23) break
    }
    if (list5 != listOf<Char>('g', 'f', 'e', 'd', 'c')) {
        return "Wrong elements for 'g' downTo 'c': $list5"
    }

    val list6 = ArrayList<Double>()
    for (i in 9.0 downTo 3.0) {
        list6.add(i)
        if (list6.size() > 23) break
    }
    if (list6 != listOf<Double>(9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0)) {
        return "Wrong elements for 9.0 downTo 3.0: $list6"
    }

    val list7 = ArrayList<Float>()
    for (i in 9.0.toFloat() downTo 3.0.toFloat()) {
        list7.add(i)
        if (list7.size() > 23) break
    }
    if (list7 != listOf<Float>(9.0.toFloat(), 8.0.toFloat(), 7.0.toFloat(), 6.0.toFloat(), 5.0.toFloat(), 4.0.toFloat(), 3.0.toFloat())) {
        return "Wrong elements for 9.0.toFloat() downTo 3.0.toFloat(): $list7"
    }

    return "OK"
}
