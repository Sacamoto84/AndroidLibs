
Flow можно представить себе как трубу, по которой запросы на следующие значения движутся в одном направлении, а соответствующие произведенные значения движутся в другом направлении. Когда поток завершается или возникает исключение, эта информация также передается и закрывает промежуточные этапы на пути. Таким образом, поскольку все они движутся, мы можем прослушивать значения, исключения или другие характеристические события (например, начало или завершение). Для этого мы используем методы, такие как `onEach`, `onStart`, `onCompletion`, `onEmpty` и `catch`. Давайте разберем их по очереди.

## onEach

Для реакции на каждое переданное значение используется функция `onEach`.

```kotlin
suspend fun main() {
	flowOf(1, 2, 3, 4)
		.onEach { print(it) }
		.collect() // 1234
}
```

Лямбда-выражение `onEach` является приостанавливающим, и элементы обрабатываются один за другим в порядке (`sequentially`). Таким образом, если мы добавим `delay` в `onEach`, мы задержим каждое значение по мере его передачи.

```kotlin
suspend fun main() {
	flowOf(1, 2)
		.onEach { delay(1000) }
		.collect { println(it) }
}
// (1 sec)
// 1
// (1 sec)
// 2
```

## onStart

Функция `onStart` устанавливает слушатель, который должен быть вызван сразу после запуска потока, то есть после вызова терминальной операции. Важно отметить, что `onStart` не ожидает первый элемент: он вызывается, когда мы запрашиваем первый элемент.

```kotlin
suspend fun main() {
	flowOf(1, 2)
		.onEach { delay(1000) }
		.onStart { println("Before") }
		.collect { println(it) }
}
// Before
// (1 sec)
// 1
// (1 sec)
// 2
```

Важно знать, что в `onStart` (как и в `onCompletion`, `onEmpty` и `catch`) мы можем генерировать элементы. Такие элементы будут переданы по потоку от этого места вниз.

```kotlin
suspend fun main() {
	flowOf(1, 2)
		.onEach { delay(1000) }
		.onStart { emit(0) }
		.collect { println(it) }
}
// 0
// (1 sec)
// 1
// (1 sec)
// 2
```

## onCompletion

Существует несколько способов завершения потока данных. Самый распространенный - когда построитель потока завершен (т.е. был отправлен последний элемент), но это также происходит в случае неперехваченного исключения или отмены корутины. Во всех этих случаях мы можем добавить слушатель завершения потока с помощью метода `onCompletion`

```kotlin
suspend fun main() = coroutineScope {
	flowOf(1, 2)
		.onEach { delay(1000) }
		.onCompletion { println("Completed") }
		.collect { println(it) }
}
// (1 sec)
// 1
// (1 sec)
// 2
// Completed

suspend fun main() = coroutineScope {
	val job = launch {
		flowOf(1, 2)
		.onEach { delay(1000) }
		.onCompletion { println("Completed") }
		.collect { println(it) }
	}
	delay(1100)
	job.cancel()
}
// (1 sec)
// 1
// (0.1 sec)
// Completed
```

В Android мы часто используем `onStart` для отображения индикатора выполнения (индикатора ожидания ответа от сети), а `onCompletion` для его скрытия.

```kotlin
fun updateNews() {
	scope.launch {
		newsFlow()
			.onStart { showProgressBar() }
			.onCompletion { hideProgressBar() }
			.collect { view.showNews(it) }
	}
}
```

## onEmpty

Поток данных может завершиться без передачи любого значения, что может быть признаком неожиданного события. Для таких случаев существует функция `onEmpty`, которая вызывает указанное действие, когда поток завершается без передачи элементов. Метод `onEmpty` может использоваться для передачи какого-то значения по умолчанию.

```kotlin
suspend fun main() = coroutineScope {
	flow<List<Int>> { delay(1000) }
		.onEmpty { emit(emptyList()) }
		.collect { println(it) }
}
// (1 sec)
// []
```

## catch

В любой момент создания или обработки потока данных может возникнуть исключение. Такое исключение будет передаваться вниз, закрывая каждый шаг обработки по пути, однако его можно перехватить и обработать. Для этого можно использовать метод `catch`. Этот обработчик получает исключение в качестве аргумента и позволяет выполнять операции восстановления.

```kotlin
class MyError : Throwable("My error")

val flow = flow {
	emit(1)
	emit(2)
	throw MyError()
}

suspend fun main(): Unit {
	flow.onEach { println("Got $it") }
		.catch { println("Caught $it") }
		.collect { println("Collected $it") }
}
// Got 1
// Collected 1
// Got 2
// Collected 2
// Caught MyError: My error
```

В приведенном выше примере обратите внимание, что `onEach` не реагирует на исключение. То же самое происходит с другими функциями, такими как `map`, `filter` и т. д. Вызывается только обработчик `onCompletion`.

Метод `catch` перехватывает исключение, предотвращая его проброс. Предыдущие шаги уже были выполнены, но `catch` всё ещё может генерировать новые значения и продолжать выполнение оставшейся части потока данных.

```kotlin
val flow = flow {
	emit("Message1")
	throw MyError()
}

suspend fun main(): Unit {
	flow.catch { emit("Error") }
		.collect { println("Collected $it") }
}
// Collected Message1
// Collected Error
```

Метод `catch` реагирует только на исключения, выбрасываемые в функции, определённой выше по потоку данных (можно представить, что исключение должно быть перехвачено по мере его передачи вниз по потоку).

![[./img/306.png]]

На платформе Android часто используется метод `catch` для отображения исключений, возникших в потоке данных.

```kotlin
fun updateNews() {
	scope.launch {
		newsFlow()
			.catch { view.handleError(it) }
			.onStart { showProgressBar() }
			.onCompletion { hideProgressBar() }
			.collect { view.showNews(it) }
	}
}
```

Мы также можем использовать метод `catch` для генерации стандартных данных, которые будут отображаться на экране, например, пустого списка.

```kotlin
fun updateNews() {
	scope.launch {
		newsFlow()
			.catch {
				view.handleError(it)
				emit(emptyList())
			}
			.onStart { showProgressBar() }
			.onCompletion { hideProgressBar() }
			.collect { view.showNews(it) }
	}
}
```

## Uncaught exceptions Неперехваченные исключения

Неперехваченные исключения в потоке немедленно отменяют этот поток, а операция `collect` повторно выбрасывает это исключение. Это типичное поведение для приостанавливающих функций, и `coroutineScope` ведёт себя аналогично. Исключения могут быть перехвачены вне потока с использованием классического блока `try-catch`.

```kotlin
val flow = flow {
	emit("Message1")
	throw MyError()
}

suspend fun main(): Unit {
	try {
		flow.collect { println("Collected $it") }
	} catch (e: MyError) {
		println("Caught")
	}
}
// Collected Message1
// Caught
```

307