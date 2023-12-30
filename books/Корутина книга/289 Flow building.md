Каждый поток должен начинаться откуда-то. Есть много способов сделать это, в зависимости от того, что нам нужно. В этой главе мы сосредоточимся на наиболее важных вариантах.

## Flow from raw values

Самый простой способ создать поток - использовать функцию `flowOf`, где мы просто определяем, какие значения должен содержать этот поток (похоже на функцию `listOf` для списка).

```kotlin
suspend fun main() {
	flowOf(1, 2, 3, 4, 5)
		.collect { print(it) } // 12345
}
```

Иногда нам может потребоваться поток без значений. Для этого у нас есть функция `emptyFlow()` (аналогичная функции `emptyList` для списка).

```kotlin
suspend fun main() {
	emptyFlow<Int>()
	.collect { print(it) } // (nothing)
}
```
## Converters

Мы также можем преобразовать любой `Iterable`, `Iterator` или `Sequence` в поток, используя функцию `asFlow`.

```kotlin
suspend fun main() {
	listOf(1, 2, 3, 4, 5)
		// or setOf(1, 2, 3, 4, 5)
		// or sequenceOf(1, 2, 3, 4, 5)
		.asFlow()
		.collect { print(it) } // 12345
}
```

Эти функции создают поток элементов, доступных немедленно. Они полезны для запуска потока элементов, которые мы затем можем обрабатывать с помощью функций обработки потока.

## Converting a function to a flow

`Flow` часто используется для представления единичного значения, отложенного во времени (подобно `Single` в `RxJava`). Поэтому имеет смысл преобразовать приостанавливающую функцию в поток. Результат этой функции будет единственным значением в этом потоке. Для этого существует функция-расширение `asFlow`, которая работает с типами функций (как `suspend () -> T`, так и `() -> T`). Здесь она используется для преобразования приостанавливающего лямбда-выражения в поток.

```kotlin
suspend fun main() {
val function = suspend {
	// this is suspending lambda expression
	delay(1000)
	"UserName"
}

function.asFlow()
	.collect { println(it) }

}
// (1 sec)
// UserName
```

Чтобы преобразовать обычную функцию, нам сначала нужно на нее ссылаться. В Kotlin для этого используется оператор `::`

```kotlin
suspend fun getUserName(): String {
	delay(1000)
	return "UserName"
}

suspend fun main() {
	::getUserName
		.asFlow()
		.collect { println(it) }
}
// (1 sec)
// UserName
```

## Flow and Reactive Streams

Если вы используете реактивные потоки (например, Reactor, RxJava 2.x. или RxJava 3.x.) в вашем приложении, вам не нужно делать больших изменений в вашем коде. Все объекты, такие как `Flux`, `Flowable` или `Observable`, реализуют интерфейс `Publisher`, который может быть преобразован в `Flow` с помощью функции `asFlow` из библиотеки kotlinx-coroutines-reactive.
```kotlin
suspend fun main() = coroutineScope {
	Flux.range(1, 5).asFlow()
		.collect { print(it) } // 12345
	Flowable.range(1, 5).asFlow()
		.collect { print(it) } // 12345
	Observable.range(1, 5).asFlow()
		.collect { print(it) } // 12345
}
```

Чтобы выполнить обратное преобразование, вам потребуются более конкретные библиотеки. С помощью kotlinx-coroutines-reactor вы можете преобразовать `Flow` в `Flux`. А с помощью kotlinx-coroutines-rx3 (или kotlinx-coroutines-rx2) вы можете преобразовать Flow в `Flowable` или `Observable`.

```kotlin
suspend fun main(): Unit = coroutineScope {
	val flow = flowOf(1, 2, 3, 4, 5)

	flow.asFlux()
		.doOnNext { print(it) } // 12345
		.subscribe()

	flow.asFlowable()
		.subscribe { print(it) } // 12345

	flow.asObservable()
		.subscribe { print(it) } // 12345
}
```

## Flow builders

Самый популярный способ создания потока - использование построителя потока (flow builder), которым мы уже пользовались в предыдущих главах. Он ведет себя аналогично построителю последовательности для создания последовательности или построителю produce для создания канала. Мы начинаем построитель с вызова функции flow, и внутри его лямбда-выражения мы передаем следующие значения с помощью функции `emit`. Мы также можем использовать `emitAll` для передачи всех значений из канала или потока `(emitAll(flow)` является сокращением для` flow.collect { emit(it) })`.

```kotlin
fun makeFlow(): Flow<Int> = flow {
	repeat(3) { num ->
		delay(1000)
		emit(num)
	}
}

suspend fun main() {
	makeFlow()
		.collect { println(it) }
}
// (1 sec)
// 0
// (1 sec)
// 1
// (1 sec)
// 2
```

Этот построитель уже использовался в предыдущих главах и будет использоваться много раз в предстоящих, поэтому мы увидим множество примеров его применения. На данный момент давайте вернемся к одному из примеров из главы о `Sequence builder`. Здесь построитель потока используется для создания потока пользователей, которых необходимо запрашивать страница за страницей из нашего сетевого API.

```kotlin
fun allUsersFlow(
	api: UserApi
): Flow<User> = flow {
	var page = 0
	do {
		val users = api.takePage(page++) // suspending
		emitAll(users)
	} while (!users.isNullOrEmpty())
}
```

## Understanding flow builder

flow builder - самый базовый способ создания потока. Все остальные варианты основаны на нем.

```kotlin
public fun <T> flowOf(vararg elements: T): Flow<T> = flow {
	for (element in elements) {
		emit(element)
	}
}
```

Когда мы понимаем, как работает этот построитель, мы понимаем, как работает поток. Построитель Flow очень прост внутри: он просто создает объект, реализующий интерфейс `Flow`, который просто вызывает функцию `block` внутри метода `collect`.

```kotlin
fun <T> flow(
	block: suspend FlowCollector<T>.() -> Unit
): Flow<T> = object : Flow<T>() {
	override suspend fun collect(collector: FlowCollector<T>){
		collector.block()
	}
}

interface Flow<out T> {
	suspend fun collect(collector: FlowCollector<T>)
}

fun interface FlowCollector<in T> {
	suspend fun emit(value: T)
}
```

Конечно, давайте разберем, как работает следующий код:

```kotlin
fun main() = runBlocking {
	flow { // 1
		emit("A")
		emit("B")
		emit("C")
	}.collect { value -> // 2
		println(value)
	}
}
// A
// B
// C
```

Когда мы вызываем построитель flow, мы просто создаем объект. Однако вызов `collect` означает вызов функции `block` в интерфейсе коллектора. Функция `block` в этом примере - это лямбда-выражение, определенное на 1. Ее получателем является `collector`, который определен на 2 с помощью лямбда-выражения. Когда мы определяем функциональный интерфейс (например, `FlowCollector`) с использованием лямбда-выражения, тело этого лямбда-выражения будет использоваться в качестве реализации единственной ожидаемой функции этим интерфейсом, которая в данном случае является функцией `emit`. Таким образом, тело функции `emit` - это `println(value)`. Таким образом, когда мы вызываем `collect`, мы начинаем выполнение лямбда-выражения, определенного на 1, и когда оно вызывает `emit`, оно вызывает лямбда-выражение, определенное на 2. Вот как работает поток. Все остальное строится на этом базовом принципе.

## channelFlow

Поток (Flow) представляет собой "холодный" поток данных, поэтому он производит значения по запросу, когда они нужны. Если рассматривать представленный выше пример `allUsersFlow`, следующая страница пользователей будет запрошена только тогда, когда получатель потока запросит ее. Это желательно в некоторых ситуациях. Например, представьте, что мы ищем конкретного пользователя. Если он находится на первой странице, нам не нужно запрашивать дополнительные страницы. Чтобы увидеть это на практике, в приведенном ниже примере мы создаем следующие элементы с использованием построителя потока. Обратите внимание, что следующая страница запрашивается лениво, только когда это требуется.

```kotlin
data class User(val name: String)

interface UserApi {
	suspend fun takePage(pageNumber: Int): List<User>
}

class FakeUserApi : UserApi {
	private val users = List(20) { User("User$it") }
	private val pageSize: Int = 3

	override suspend fun takePage(
		pageNumber: Int
	): List<User> {
		delay(1000) // suspending
		return users
			.drop(pageSize * pageNumber)
			.take(pageSize)
	}
}

fun allUsersFlow(api: UserApi): Flow<User> = flow {
	var page = 0
	do {
		println("Fetching page $page")
		val users = api.takePage(page++) // suspending
		emitAll(users.asFlow())
	} while (!users.isNullOrEmpty())
}

suspend fun main() {
	val api = FakeUserApi()
	val users = allUsersFlow(api)
	val user = users
		.first {
			println("Checking $it")
			delay(1000) // suspending
			it.name == "User3"
		}
	println(user)
}
// Fetching page 0
// (1 sec)
// Checking User(name=User0)
// (1 sec)
// Checking User(name=User1)
// (1 sec)
// Checking User(name=User2)
// (1 sec)
// Fetching page 1
// (1 sec)
// Checking User(name=User3)
// (1 sec)
// User(name=User3)
```

С другой стороны, могут возникать ситуации, когда нам нужно получать страницы заранее, когда мы все еще обрабатываем элементы. В данном случае это может привести к большему количеству сетевых запросов, но может также обеспечить более быстрый результат. Для достижения этой цели нам нужна независимость производства и потребления. Такая независимость типична для "горячих" потоков данных, таких как каналы. Для этого нам потребуется гибрид канала и потока. Да, это поддерживается: нам просто нужно использовать функцию `channelFlow`, которая подобна потоку, поскольку реализует интерфейс `Flow`. Этот построитель является обычной функцией и запускается с помощью терминальной операции (например, `collect`). Он также похож на канал, потому что после запуска он производит значения в отдельном корутине, не дожидаясь получателя. Следовательно, получение следующих страниц и проверка пользователей происходят параллельно.

```kotlin
fun allUsersFlow(api: UserApi): Flow<User> = channelFlow {
	var page = 0
	do {
		println("Fetching page $page")
		val users = api.takePage(page++) // suspending
		users?.forEach { send(it) }
	} while (!users.isNullOrEmpty())
}

suspend fun main() {
	val api = FakeUserApi()
	val users = allUsersFlow(api)
	val user = users
	.first {
		println("Checking $it")
		delay(1000)
		it.name == "User3"
	}
	println(user)
}
// Fetching page 0
// (1 sec)
// Checking User(name=User0)
// Fetching page 1
// (1 sec)
// Checking User(name=User1)
// Fetching page 2
// (1 sec)
// Checking User(name=User2)
// Fetching page 3
// (1 sec)
// Checking User(name=User3)
// Fetching page 4
// (1 sec)
// User(name=User3)
```

Внутри `channelFlow` мы оперируем с `ProducerScope<T>`. `ProducerScope` представляет тот же тип, что и используется в построителе `produce`. Он реализует `CoroutineScope`, поэтому мы можем использовать его для запуска новых корутин с помощью построителей. Для создания элементов мы используем `send` вместо `emit`. Мы также можем получить доступ к каналу или управлять им напрямую с помощью функций `SendChannel`.

```kotlin
interface ProducerScope<in E> :
	CoroutineScope, SendChannel<E> {

	val channel: SendChannel<E>
}
```

Типичным случаем использования `channelFlow` является необходимость независимого вычисления значений. Для поддержки этого `channelFlow` создает область корутина, поэтому мы можем напрямую запускать построители корутин, такие как `launch`. Код ниже не будет работать для `flow`, потому что он не создает необходимую область видимости для построителей корутин.

```kotlin
fun <T> Flow<T>.merge(other: Flow<T>): Flow<T> =
	channelFlow {
		launch {
			collect { send(it) }
		}
	other.collect { send(it) }
}

fun <T> contextualFlow(): Flow<T> = channelFlow {
	launch(Dispatchers.IO) {
		send(computeIoValue())
	}
	launch(Dispatchers.Default) {
		send(computeCpuValue())
	}
}
```

Как и все остальные корутины, `channelFlow` не завершается, пока все его дочерние корутины не находятся в терминальном состоянии.

## callbackFlow

Предположим, вам нужен поток событий, на которые вы слушаете, например, клики пользователей или другие виды действий. Процесс прослушивания должен быть независимым от процесса обработки этих событий, поэтому `channelFlow` был бы хорошим выбором. Однако есть более подходящий вариант: `callbackFlow`. Долгое время не было различий между `channelFlow` и `callbackFlow`. В версии 1.3.4 были внесены небольшие изменения для уменьшения вероятности ошибок при использовании обратных вызовов. Однако самое большое различие заключается в том, как люди понимают эти функции: `callbackFlow` предназначен для обертывания обратных вызовов. Внутри `callbackFlow` мы также оперируем с `ProducerScope<T>`. Вот несколько функций, которые могут быть полезны при обертывании обратных вызовов:

• `awaitClose { ... }` - функция, которая приостанавливает выполнение до закрытия канала. После его закрытия она вызывает свой аргумент. `awaitClose` очень важен для `callbackFlow`. Без него корутина завершится сразу после регистрации обратного вызова. Это естественно для корутины: ее тело завершено, и у нее нет дочерних элементов, за которыми нужно ждать, поэтому она завершается. Мы используем `awaitClose` (даже с пустым телом), чтобы предотвратить это и продолжать прослушивать элементы до закрытия канала другим способом. 
• `trySendBlocking(value)` - аналогично `send`, но блокирует поток вместо приостановки, поэтому его можно использовать с не-приостанавливающими функциями. 
• `close()` - завершает этот канал. 
• `cancel(throwable)` - завершает этот канал и отправляет исключение в поток.

Вот типичный пример использования `callbackFlow`:

```kotlin
fun flowFrom(api: CallbackBasedApi): Flow<T> = callbackFlow {
	val callback = object : Callback {
		override fun onNextValue(value: T) {
		trySendBlocking(value)
	}
	
	override fun onApiError(cause: Throwable) {
		cancel(CancellationException("API Error", cause))
	}
	
	override fun onCompleted() = channel.close()
	}
	
	api.register(callback)
	awaitClose { api.unregister(callback) }
}
```

В этой главе мы рассмотрели различные способы создания потоков данных. Существует множество функций для запуска потока данных, начиная с простых, таких как `flowOf` или `emptyFlow`, и заканчивая преобразованиями типов, такими как `asFlow`, и построителями потоков. Самый простой из построителей потоков - это просто функция `flow`, где вы можете использовать функцию `emit` для создания следующих значений. Также существуют построители `channelFlow` и `callbackFlow`, которые создают поток данных с некоторыми характеристиками канала. Каждой из этих функций есть свои собственные применения, и полезно знать их, чтобы раскрыть полный потенциал Flow.