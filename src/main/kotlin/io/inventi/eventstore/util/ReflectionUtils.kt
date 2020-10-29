package io.inventi.eventstore.util

import com.fasterxml.jackson.databind.util.ClassUtil
import java.lang.reflect.Method

fun Class<*>.findMethods(name: String) = getAllMethods().filter { it.name == name }

fun Class<*>.getAllMethods(): List<Method> {
    val result = mutableListOf<Method>()
    var cls = thisOrSuperClass()
    while (cls != Any::class.java) {
        result.addAll(ClassUtil.getClassMethods(cls))
        cls = cls.superclass
    }
    return result
}

fun <T> Class<T>.thisOrSuperClass(): Class<in T> = if (isAnonymousClass) superclass else this
