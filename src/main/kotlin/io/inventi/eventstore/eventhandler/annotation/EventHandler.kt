package io.inventi.eventstore.eventhandler.annotation


@Target(AnnotationTarget.FUNCTION, AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class EventHandler(val skipWhenReplaying: Boolean = false)