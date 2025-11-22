import groovy.lang.Closure

/**
  * I hate "`use namespace X;` via anonymous subtyping + instance initializer" or "FLWIR" with passion.<p/>
  * Also yes, this is a kind of freestanding Gradle Kotlin library as well, 
  *     provides helper to removes "guess what is `this` type and ton of implicit symbols in this scope"
  *     so you can do Kotlin-Groovy-Gradle-Java without resorting to JetBrains's tools.
  */
object Javaificator {
    /** Ah, "function literal with implicit receiver" or FLWIR, the bane of my existence. */
    fun <T> lambdaToFLWIR(sideEffectLambda: (T) -> Unit): T.() -> Unit    { return { sideEffectLambda(this) } }
    /** 
      * See Gradle Javadoc & Kotlindoc: https://docs.gradle.org/current/javadoc/org/gradle/api/Action.html
      * Fun historical lore: Originally, I write this function in Gradle 8~ and it doesn't require type constraint. 
      *     Apparently, since github.com:gradle/gradle@cb0c615 (which seems cover Gradle 9+) `Action<T>` gains non-nullable type constraint (`T: Any`).
      */
    fun <T: Any> lambdaToAction(sideEffectLambda: (T) -> Unit): Action<T> { return Action<T> { sideEffectLambda(this) } }
    /** This time it's Groovy. Groovydoc: https://docs.groovy-lang.org/latest/html/api/groovy/lang/Closure.html */
    fun <T> lambdaToClosure(sideEffectLambda: (T) -> Unit): Closure<Any?> { return closureOf<T>{ sideEffectLambda(this) } }
}
/**
  * Debugging function: Namespace polluting `Any` extension function designed to help me cope through Kotlin sugar insanity.
  * I promise I will never use extension function other than this and pollute namespaces.
  */
fun Any.stopUsingFLWIRAndTellMeWhatIsThisType(): Unit {
    println("Here: ${this::class} | Super: ${this::class.supertypes}")
}



project.plugins{ // This one literally cannot be Javaificator'd
    this.id("java-library");
}

java.getToolchain()
    .getLanguageVersion()
    .set(JavaLanguageVersion.of(21))

project.getRepositories()
    .configure(Javaificator.lambdaToClosure{ repository: RepositoryHandler ->
        repository.mavenCentral()
    })

sourceSets {
    test {
        java.setSrcDirs(listOf("src/test/"))
    }
}

testing{
    this.suites{
        val test by this.getting(JvmTestSuite::class) {
            useJUnitJupiter() 
        }
    }
}
