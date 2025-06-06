package com.mongodb.jbplugin.mql.parser

import com.mongodb.jbplugin.mql.adt.Either
import com.mongodb.jbplugin.mql.adt.EitherInclusive
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * A Parser is a suspendable function that returns either an error or a valid output from a
 * lazy input. Parsers can be composed using different functions, like filter, map, mapError...
 * All these compositions return a new Parser.
 *
 * To understand how a parser works, we need to think in a pipeline: the output of the parser is
 * the input of the next parser.
 *
 * Parser are typesafe on input, error and output.
 *
 * Some examples, let's say we want to get the collection reference, if it's know, on a query. We
 * would define the parser as the following:
 *
 * ```kt
 * val knownCollRef = knownCollectionReference<S>();
 * ```
 *
 * Note how we specify S in the knownCollectionReference parser. It's because S is generic in our
 * model. This will return a parser of the following type:
 *
 * ```kt
 * Parser<Node<S>, NoCollectionReference, HasCollectionReference.Known<S>>
 * ```
 *
 * If we want to get the namespace object from it, we would create a new parser from this one, in
 * this case, using the .map combinator:
 *
 * ```kt
 * val knownNamespace = knownCollRef.map { it.namespace }
 * ```
 *
 * The .map combinator wil create a new parser, that receives a Node<S>, because the input of
 * knownCollectionReference<S> is a Node<S>, and the output is a Namespace object. The final type
 * would be:
 *
 * ```kt
 * Parser<Node<S>, NoCollectionReference, Namespace>
 * ```
 */
typealias Parser<I, E, O> = suspend (I) -> Either<E, O>

data object ElementDoesNotMatchFilter

/**
 * Filter returns a Parser where the input must match the provided predicate, or returns a failing
 * parser.
 *
 * @param filterFn The filter function.
 */
fun <I, E, O> Parser<I, E, O>.filter(
    filterFn: suspend (O) -> Boolean
): Parser<I, Either<E, ElementDoesNotMatchFilter>, O> {
    return { input ->
        when (val result = this(input)) {
            is Either.Left -> Either.left(Either.left(result.value))
            is Either.Right -> when (filterFn(result.value)) {
                true -> Either.right(result.value)
                false -> Either.left(Either.right(ElementDoesNotMatchFilter))
            }
        }
    }
}

/**
 * Returns a new parser that maps the output to a new type.
 */
fun <I, E, O> Parser<I, E, O>.anyError(): Parser<I, Any, O> {
    return this as Parser<I, Any, O>
}

/**
 * Returns a new parser that maps the output to a new type.
 */
fun <I, E, O, OO> Parser<I, E, O>.map(mapFn: suspend (O) -> OO): Parser<I, E, OO> {
    return { input ->
        when (val result = this(input)) {
            is Either.Left -> Either.left(result.value)
            is Either.Right -> Either.right(mapFn(result.value))
        }
    }
}

/**
 * Returns a new parser that maps the output to a new type that can be an error or a success
 * value.
 */
fun <I, E, O, EE, OO> Parser<I, E, O>.flatMap(
    mapFn: suspend (O) -> Either<EE, OO>
): Parser<I, Either<E, EE>, OO> {
    return { input ->
        when (val result = this(input)) {
            is Either.Left -> Either.left(Either.left(result.value))
            is Either.Right -> when (val mappingResult = mapFn(result.value)) {
                is Either.Left -> Either.left(Either.right(mappingResult.value))
                is Either.Right -> Either.right(mappingResult.value)
            }
        }
    }
}

/**
 * Returns a parser that maps the error of the previous parser.
 */
fun <I, E, O> Parser<I, E, O>.recoverError(mapFn: (E) -> O): Parser<I, E, O> {
    return { input ->
        when (val result = this(input)) {
            is Either.Left -> Either.right(mapFn(result.value))
            is Either.Right -> result
        }
    }
}

/**
 * Parser that returns the element <index> in the input list.
 */
data object IndexOutOfBounds
fun <I, E, O> Parser<I, E, List<O>>.nth(index: Int): Parser<I, Either<E, IndexOutOfBounds>, O> {
    return { input ->
        when (val result = this(input)) {
            is Either.Left -> Either.left(Either.left(result.value))
            is Either.Right -> {
                val element = result.value.getOrNull(index)
                if (element == null) {
                    Either.left(Either.right(IndexOutOfBounds))
                } else {
                    Either.right(element)
                }
            }
        }
    }
}

/**
 * Returns a parser that runs both this, and the parameter parser, in parallel, and aggregates
 * the results.
 */
fun <I, E, O, E2, O2> Parser<I, E, O>.zip(
    second: Parser<I, E2, O2>
): Parser<I, EitherInclusive<E, E2>, Pair<O, O2>> {
    return { input ->
        coroutineScope {
            val firstJob = async { this@zip(input) }
            val secondJob = async { second(input) }

            when (val firstResult = firstJob.await()) {
                is Either.Left -> when (val secondResult = secondJob.await()) {
                    is Either.Left -> Either.left(firstResult.value to secondResult.value)
                    else -> Either.left(firstResult.value to null)
                }
                is Either.Right -> when (val secondResult = secondJob.await()) {
                    is Either.Left -> Either.left(null to secondResult.value)
                    is Either.Right -> Either.right(firstResult.value to secondResult.value)
                }
            }
        }
    }
}

/**
 * Returns a parser that runs a parser for each element of the output list.
 */
fun <I, E, O, E2, O2> Parser<I, E, List<O>>.mapMany(
    parser: Parser<O, E2, O2>
): Parser<I, Either<E, List<E2>>, List<O2>> {
    return { input ->
        when (val result = this(input)) {
            is Either.Left -> Either.left(Either.left(result.value))
            is Either.Right -> {
                val eachResult = result.value.map { parser(it) }.groupBy { either ->
                    either is Either.Right
                }

                val errorResult = eachResult[false]
                val okResult = eachResult[true]

                if (errorResult?.isNotEmpty() == true) {
                    Either.left(
                        Either.right(
                            errorResult.mapNotNull {
                                it as? Either.Left
                            }.map { it.value }
                        )
                    )
                } else {
                    Either.right(
                        okResult?.map { it as Either.Right }?.map { it.value } ?: emptyList()
                    )
                }
            }
        }
    }
}

/**
 * Returns a parser that runs a parser for each element of the output list.
 */
fun <I, E, O> Parser<I, E, List<O?>>.filterNotNullMany(): Parser<I, Either<E, Nothing>, List<O>> {
    return { input ->
        when (val result = this(input)) {
            is Either.Left -> Either.left(Either.left(result.value))
            is Either.Right -> {
                Either.right(result.value.filterNotNull())
            }
        }
    }
}

data object NoConditionFulfilled

fun <I, E> equals(toValue: I): Parser<I, E, Boolean> {
    return { input ->
        Either.right(input == toValue)
    }
}

fun <I, E> not(parser: Parser<I, E, Boolean>): Parser<I, E, Boolean> {
    return parser.map { !it }
}

fun <I, E, O> otherwise(defaultValue: () -> O): Parser<I, E, O> {
    val thenDefaultValue: Parser<I, E, O> = { input: I -> Either.right(defaultValue()) }
    return thenDefaultValue
}

fun <I, E, EA, O> Parser<I, E, O>.matches(
    filterFn: Parser<O, EA, Boolean>
): Parser<I, Either<E, ElementDoesNotMatchFilter>, O> {
    return { input ->
        when (val result = this(input)) {
            is Either.Left -> Either.left(Either.left(result.value))
            is Either.Right -> when (filterFn(result.value).orElse { false }) {
                true -> Either.right(result.value)
                false -> Either.left(Either.right(ElementDoesNotMatchFilter))
            }
        }
    }
}

/**
 * Returns a parser that checks that the source parser would fail or not.
 */
fun <I, E, O> Parser<I, E, O>.matches(): Parser<I, E, Boolean> {
    return { input ->
        when (val result = this(input)) {
            is Either.Left -> Either.left(result.value)
            is Either.Right -> Either.right(true)
        }
    }
}

/**
 * Executes the first parser where it's condition fulfills, and return it's result.
 */
fun <I, E, O> cond(
    vararg branches: Pair<Parser<I, *, Boolean>, Parser<I, E, O>>
): Parser<I, Either<NoConditionFulfilled, E>, O> {
    return { input ->
        suspend fun doesInputMatchCondition(condition: Parser<I, *, Boolean>): Boolean {
            return when (val result = condition(input)) {
                is Either.Left -> false
                is Either.Right -> result.value
            }
        }

        val branch = branches.firstOrNull { doesInputMatchCondition(it.first) }
        when (val result = branch?.second?.invoke(input)) {
            is Either.Left -> Either.left(Either.right(result.value))
            is Either.Right -> Either.right(result.value)
            null -> Either.left(Either.left(NoConditionFulfilled))
        }
    }
}

/**
 * Uses the first matching parser.
 */
fun <I, E, O> first(
    vararg parsers: Parser<I, E, O>
): Parser<I, Either<NoConditionFulfilled, E>, O> {
    val branches = parsers.map { it.matches() to it }.toTypedArray()
    return cond(*branches)
}

suspend fun <I, E, O> Parser<I, E, O>.parse(input: I): Either<E, O> {
    return this@parse(input)
}

fun <I, E> requireNonNull(err: E): Parser<I?, E, I> {
    return { input ->
        if (input == null) {
            Either.left(err)
        } else {
            Either.right(input)
        }
    }
}

@Deprecated("Only use in development.")
fun <I, E, O> Parser<I, E, O>.debug(message: String): Parser<I, E, O> {
    return { input ->
        val result = this(input)
        println(":: $message -> $input > $result ")
        result
    }
}
