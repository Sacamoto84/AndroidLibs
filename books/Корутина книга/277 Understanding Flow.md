Интерфейс Flow в Kotlin Coroutines на самом деле намного более простой, чем думают большинство разработчиков. Это всего лишь определение операций для выполнения. Это похоже на приостанавливающееся лямбда-выражение, но с некоторыми дополнительными элементами. В этой главе я покажу вам, как реализовать интерфейс Flow и построитель потока, преобразуя лямбда-выражение шаг за шагом. Это должно помочь вам глубоко понять, как работает Flow. Эта глава для любознательных умов, которые любят полностью понимать инструменты, которыми пользуются. Если это не про вас, не стесняйтесь пропустить эту главу. Если вы решите ее прочитать, надеюсь, она вам понравится.

## Understanding Flow

Начнем нашу историю с простого лямбда-выражения. Каждое лямбда-выражение может быть определено один раз и затем вызываться несколько раз.

```kotlin
fun main() {
	val f: () -> Unit = {
		print("A")
		print("B")
		print("C")
	}
f() // ABC
f() // ABC
}
```

Чтобы сделать его более интересным, давайте сделаем наше лямбда-выражение приостанавливающим и добавим внутри него некоторую задержку. Обратите внимание, что каждый вызов такого лямбда-выражения последователен, поэтому следует избегать вызова следующего, пока предыдущий не завершится.

```kotlin
suspend fun main() {
	val f: suspend () -> Unit = {
		print("A")
		delay(1000)
		print("B")
		delay(1000)
		print("C")
	}
	f()
	f()
}
// A
// (1 sec)
// B
// (1 sec)
// C
// A
// (1 sec)
// B
// (1 sec)
// C
```

Лямбда-выражение может иметь параметр, который может представлять функцию. Давайте назовем этот параметр `emit`. Таким образом, при вызове лямбда-выражения `f` вам нужно указать другое лямбда-выражение, которое будет использоваться как `emit`.

```kotlin
suspend fun main() {
	val f: suspend ((String) -> Unit) -> Unit = { emit ->
		emit("A")
		emit("B")
		emit("C")
	}
	f { print(it) } // ABC
	f { print(it) } // ABC
}
```

Фактически `emit` также должен быть приостанавливающей функцией. Наш тип функции уже становится достаточно сложным, поэтому мы упростим его. Мы можем упростить это, определив интерфейс функции `FlowCollector` с абстрактным методом `emit`. Мы будем использовать этот интерфейс вместо типа функции. Трюк в том, что функциональные интерфейсы могут быть определены с помощью лямбда-выражений, поэтому нам не нужно изменять вызов `f`.

```kotlin
import kotlin.*

fun interface FlowCollector {
	suspend fun emit(value: String)
}

suspend fun main() {
	val f: suspend (FlowCollector) -> Unit = {
		it.emit("A")
		it.emit("B")
		it.emit("C")
	}
	f { print(it) } // ABC
	f { print(it) } // ABC
}
```

Вызов `emit` в таком виде неудобен; вместо этого мы сделаем `FlowCollector` получателем. Благодаря этому внутри нашего лямбда-выражения есть получатель (ключевое слово `this`) типа `FlowCollector`. Это означает, что мы можем вызывать `this.emit` или просто `emit`. Вызов `f` остается тем же.

```kotlin
fun interface FlowCollector {
	suspend fun emit(value: String)
}

suspend fun main() {
	val f: suspend FlowCollector.() -> Unit = {
		emit("A")
		emit("B")
		emit("C")
	}
	f { print(it) } // ABC
	f { print(it) } // ABC
}
```

Вместо передачи лямбда-выражений мы предпочитаем иметь объект, реализующий интерфейс. Мы назовем этот интерфейс Flow и обернем наше определение в объектное выражение.

```kotlin
import kotlin.*

fun interface FlowCollector {
	suspend fun emit(value: String)
}

interface Flow {
	suspend fun collect(collector: FlowCollector)
}

suspend fun main() {
	val builder: suspend FlowCollector.() -> Unit = {
		emit("A")
		emit("B")
		emit("C")
	}
	val flow: Flow = object : Flow {
		override suspend fun collect(
		collector: FlowCollector
		) {
			collector.builder()
		}
	}
	flow.collect { print(it) } // ABC
	flow.collect { print(it) } // ABC
}
```

Наконец, давайте определим функцию построителя потока (flow builder), чтобы упростить создание нашего потока.

```kotlin
import kotlin.*

fun interface FlowCollector {
	suspend fun emit(value: String)
}

interface Flow {
	suspend fun collect(collector: FlowCollector)
}

fun flow(
builder: suspend FlowCollector.() -> Unit
) = object : Flow {
	override suspend fun collect(collector: FlowCollector) {
		collector.builder()
	}
}

suspend fun main() {
	val f: Flow = flow {
		emit("A")
		emit("B")
	emit("C")
	}
	f.collect { print(it) } // ABC
	f.collect { print(it) } // ABC
}
```

Последнее изменение, которое нам нужно сделать, это заменить тип `String` на обобщенный параметр типа, чтобы позволить передавать и собирать значения любого типа.

```kotlin
import kotlin.*

fun interface FlowCollector<T> {
	suspend fun emit(value: T)
}

interface Flow<T> {
	suspend fun collect(collector: FlowCollector<T>)
}

fun <T> flow(
builder: suspend FlowCollector<T>.() -> Unit
) = object : Flow<T> {
	override suspend fun collect(
		collector: FlowCollector<T>
	) {
		collector.builder()
	}
}

suspend fun main() {
	val f: Flow<String> = flow {
		emit("A")
		emit("B")
		emit("C")
	}
	f.collect { print(it) } // ABC
	f.collect { print(it) } // ABC
}
```

Вот и всё! Это почти точная реализация `Flow`, `FlowCollector` и функции `flow`. При вызове `collect` вы вызываете лямбда-выражение из вызова построителя потока. Когда это выражение вызывает `emit`, оно вызывает лямбда-выражение, указанное при вызове `collect`. Вот как это работает.

Представленный построитель - самый базовый способ создания потока. Позже мы узнаем о других построителях, но обычно они просто используют `flow` внутри.

```kotlin
public fun <T> Iterator<T>.asFlow(): Flow<T> = flow {
	forEach { value ->
		emit(value)
	}
}

public fun <T> Sequence<T>.asFlow(): Flow<T> = flow {
	forEach { value ->
		emit(value)
	}
}

public fun <T> flowOf(vararg elements: T): Flow<T> = flow {
	for (element in elements) {
		emit(element)
	}
}
```

## How Flow processing works

`Flow` можно рассматривать немного сложнее, чем приостанавливающиеся лямбда-выражения с получателем. Однако его сила заключается во всех функциях, определенных для его создания, обработки и наблюдения. Большинство из них на самом деле очень простые внутри. Мы узнаем о них в следующих главах, но я хочу, чтобы у вас было интуитивное понимание того, что большинство из них очень просты и могут быть легко построены с использованием `flow`, `collect` и `emit`.

Возьмем, к примеру, функцию `map`, которая трансформирует каждый элемент потока. Она создает новый поток, поэтому использует построитель потока. Когда этот поток запускается, он должен запустить поток, который оборачивает; поэтому внутри построителя он вызывает метод `collect`. Когда получается элемент, `map` преобразует этот элемент и затем передает его в новый поток с помощью `emit`.

```kotlin
fun <T, R> Flow<T>.map(
	transformation: suspend (T) -> R
): Flow<R> = flow {
	collect {
		emit(transformation(it))
	}
}

suspend fun main() {
	flowOf("A", "B", "C")
		.map {
			delay(1000)
			it.lowercase()
		}
		.collect { println(it) }
}
// (1 sec)
// a
// (1 sec)
// b
// (1 sec)
// c
```

Поведение большинства методов, о которых мы узнаем в следующих главах, также просто. Важно понимать это, потому что это не только помогает нам лучше понять, как работает наш код, но также учит нас писать подобные функции.

```kotlin
fun <T> Flow<T>.filter(
	predicate: suspend (T) -> Boolean
): Flow<T> = flow {
	collect {
			if (predicate(it)) {
			emit(it)
		}
	}
}

fun <T> Flow<T>.onEach(
	action: suspend (T) -> Unit
): Flow<T> = flow {
	collect {
		action(it)
		emit(it)
	}
}

// simplified implementation
fun <T> Flow<T>.onStart(
	action: suspend () -> Unit
): Flow<T> = flow {
	action()
	collect {
		emit(it)
	}
}
```

## Flow is synchronous

Заметьте, что по своей природе `Flow` синхронен, как и приостанавливающие функции: вызов `collect` приостанавливается до завершения потока. Это также означает, что поток не запускает новые корутины. Его конкретные шаги могут это сделать, как и приостанавливающие функции, но это не является стандартным поведением для приостанавливающих функций. Большинство шагов обработки потока выполняются синхронно, поэтому задержка внутри `onEach` вводит задержку между каждым элементом, а не перед всеми элементами, как вы могли бы ожидать.

```kotlin
suspend fun main() {
	flowOf("A", "B", "C")
		.onEach { delay(1000) }
		.collect { println(it) }
}
// (1 sec)
// A
// (1 sec)
// B
// (1 sec)
// C
```

## Flow and shared states

При реализации более сложных алгоритмов обработки потоков важно знать, когда нужно синхронизировать доступ к переменным. Рассмотрим основные сценарии использования. Когда вы реализуете некоторые пользовательские функции обработки потоков, вы можете определять изменяемые состояния внутри потока без какого-либо механизма синхронизации, потому что шаг потока по своей природе синхронен.

```kotlin
fun <T, K> Flow<T>.distinctBy(
	keySelector: (T) -> K
) = flow {
	val sentKeys = mutableSetOf<K>()
	collect { value ->
			val key = keySelector(value)
			if (key !in sentKeys) {
			sentKeys.add(key)
			emit(value)
		}
	}
}
```

Вот пример, который используется внутри шага потока и всегда возвращает одинаковые результаты; переменная счетчика всегда увеличивается до 1000.

```kotlin
fun Flow<*>.counter() = flow<Int> {
	var counter = 0
	collect {
	counter++
	// to make it busy for a while
	List(100) { Random.nextLong() }.shuffled().sorted()
		emit(counter)
	}
}

suspend fun main(): Unit = coroutineScope {
	val f1 = List(1000) { "$it" }.asFlow()
	val f2 = List(1000) { "$it" }.asFlow()
		.counter()

	launch { println(f1.counter().last()) } // 1000
	launch { println(f1.counter().last()) } // 1000
	launch { println(f2.last()) } // 1000
	launch { println(f2.last()) } // 1000
}
```

Это распространенная ошибка - извлекать переменную извне шага потока в функцию. Такая переменная разделяется между всеми корутинами, которые собирают данные из одного и того же потока.` Это требует синхронизации и является характерным для всего потока, а не только для конкретной его коллекции`. Поэтому `f2.last()` возвращает 2000, а не 1000, потому что это результат подсчета элементов из двух параллельных выполнений потока.

```kotlin
fun Flow<*>.counter(): Flow<Int> {
	var counter = 0
	return this.map {
		counter++
		// to make it busy for a while
		List(100) { Random.nextLong() }.shuffled().sorted()
		counter
	}
}

suspend fun main(): Unit = coroutineScope {
	val f1 = List(1_000) { "$it" }.asFlow()
	val f2 = List(1_000) { "$it" }.asFlow()
		.counter()

	launch { println(f1.counter().last()) } // 1000
	launch { println(f1.counter().last()) } // 1000
	launch { println(f2.last()) } // less than 2000
	launch { println(f2.last()) } // less than 2000
}
```

Наконец, так же, как при использовании одних и тех же переменных в приостанавливающих функциях необходима синхронизация, переменная, используемая в потоке, требует синхронизации, если она определена вне функции, в области видимости класса или на верхнем уровне кода.

```kotlin
var counter = 0

fun Flow<*>.counter(): Flow<Int> = this.map {
	counter++
	// to make it busy for a while
	List(100) { Random.nextLong() }.shuffled().sorted()
	counter
}

suspend fun main(): Unit = coroutineScope {
	val f1 = List(1_000) { "$it" }.asFlow()
	val f2 = List(1_000) { "$it" }.asFlow()
		.counter()

	launch { println(f1.counter().last()) } // less than 4000
	launch { println(f1.counter().last()) } // less than 4000
	launch { println(f2.last()) } // less than 4000
	launch { println(f2.last()) } // less than 4000
}
```

## Заключение

Flow можно рассматривать как немного более сложный, чем приостанавливающееся лямбда-выражение с получателем, а его функции обработки просто декорируют его новыми операциями. Здесь нет никакой магии: то, как определен `Flow` и большинство его методов, является простым и прямолинейным.