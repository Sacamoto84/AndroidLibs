Поток данных обычно работает по принципу 'ленивости', поэтому его значения вычисляются по запросу. Однако бывают случаи, когда нам нужно, чтобы несколько получателей подписывались на один источник изменений. В таких ситуациях мы используем `SharedFlow`, который концептуально аналогичен рассылке по электронной почте. Также у нас есть `StateFlow`, который похож на наблюдаемое значение. Давайте пошагово разберем оба этих подхода.

## SharedFlow

Давайте начнем с `MutableSharedFlow`, который похож на канал для широковещания: любой может отправлять (вызывать) сообщения, которые будут получены всеми корутинами, которые следят за ними (собирают информацию).

```kotlin
suspend fun main(): Unit = coroutineScope {
	val mutableSharedFlow =
	MutableSharedFlow<String>(replay = 0)
	// or MutableSharedFlow<String>()

	launch {
		mutableSharedFlow.collect {
			println("#1 received $it")
		}
	}
	launch {
		mutableSharedFlow.collect {
			println("#2 received $it")
		}
	}

	delay(1000)
	mutableSharedFlow.emit("Message1")
	mutableSharedFlow.emit("Message2")
}
// (1 sec)
// #1 received Message1
// #2 received Message1
// #1 received Message2
// #2 received Message2
// (program never ends)
```

Приведенная выше программа никогда не завершается, потому что `coroutineScope` ожидает корутины, которые были запущены с помощью `launch` и продолжают прослушивать `MutableSharedFlow`. По-видимому, `MutableSharedFlow` нельзя закрыть, поэтому единственный способ исправить эту проблему - отменить весь контекст выполнения (`scope`).

`MutableSharedFlow` также может продолжать отправлять сообщения. Если мы установим параметр `replay` (по умолчанию равен 0), то будет сохранено определенное количество последних значений. Если корутина начинает наблюдать сейчас, она сначала получит эти значения. Этот кеш также может быть сброшен с помощью `resetReplayCache`.

```kotlin
suspend fun main(): Unit = coroutineScope {
	val mutableSharedFlow = MutableSharedFlow<String>(
	replay = 2,
)
	mutableSharedFlow.emit("Message1")
	mutableSharedFlow.emit("Message2")
	mutableSharedFlow.emit("Message3")

	println(mutableSharedFlow.replayCache)
	// [Message2, Message3]

	launch {
		mutableSharedFlow.collect {
			println("#1 received $it")
		}
		// #1 received Message2
		// #1 received Message3
	}

	delay(100)
	mutableSharedFlow.resetReplayCache()
	println(mutableSharedFlow.replayCache) // []
}
```

`MutableSharedFlow` концептуально аналогичен субъектам RxJava. Когда параметр `replay` установлен на 0, это аналогично `PublishSubject`. При значении replay равном 1, он подобен `BehaviorSubject`. Когда `replay` установлен в `Int.MAX_VALUE`, это аналогично `ReplaySubject`.

В Kotlin мы предпочитаем делать различие между интерфейсами, которые используются только для прослушивания, и теми, которые используются для модификации. Например, мы уже видели различие между `SendChannel`, `ReceiveChannel` и просто `Channel`. Та же самая логика применяется и здесь. `MutableSharedFlow` наследует как от `SharedFlow`, так и от `FlowCollector`. Первый используется для наблюдения, так как наследуется от `Flow`, в то время как `FlowCollector` используется для отправки `emit` значений.

```kotlin
interface MutableSharedFlow<T> :
SharedFlow<T>, FlowCollector<T> {

	fun tryEmit(value: T): Boolean
	val subscriptionCount: StateFlow<Int>
	fun resetReplayCache()
}

interface SharedFlow<out T> : Flow<T> {
	val replayCache: List<T>
}

interface FlowCollector<in T> {
	suspend fun emit(value: T)
}
```

Эти интерфейсы часто используются для предоставления только функций для отправки `emit` или только для `collect` сбора данных.

```kotlin
suspend fun main(): Unit = coroutineScope {
	val mutableSharedFlow = MutableSharedFlow<String>()
	val sharedFlow: SharedFlow<String> = mutableSharedFlow
	val collector: FlowCollector<String> = mutableSharedFlow

	launch {
		mutableSharedFlow.collect {
			println("#1 received $it")
		}

	}
	launch {
		sharedFlow.collect {
			println("#2 received $it")
		}
	}

	delay(1000)
	mutableSharedFlow.emit("Message1")
	collector.emit("Message2")

}
// (1 sec)
// #1 received Message1
// #2 received Message1
// #1 received Message2
// #2 received Message2
```

Вот пример типичного использования на Android:

```kotlin
class UserProfileViewModel {
	private val _userChanges =
		MutableSharedFlow<UserChange>()
	val userChanges: SharedFlow<UserChange> = _userChanges

	fun onCreate() {
		viewModelScope.launch {
			userChanges.collect(::applyUserChange)
		}
	}

	fun onNameChanged(newName: String) {
		// ...
		_userChanges.emit(NameChange(newName))
	}

	fun onPublicKeyChanged(newPublicKey: String) {
		// ...
		_userChanges.emit(PublicKeyChange(newPublicKey))
	}
}
```

## shareIn

Flow часто используется для отслеживания изменений, таких как действия пользователя, изменения в базе данных или новые сообщения. Мы уже знаем различные способы обработки и управления этими событиями. Мы узнали, как объединить несколько потоков данных в один. Но что, если несколько классов заинтересованы в этих изменениях, и нам бы хотелось превратить один поток в несколько? Решением является `SharedFlow`, и самым простым способом превратить `Flow` в `SharedFlow` является использование функции `shareIn`.

```kotlin
suspend fun main(): Unit = coroutineScope {
	val flow = flowOf("A", "B", "C")
		.onEach { delay(1000) }

	val sharedFlow: SharedFlow<String> = flow.shareIn(
		scope = this,
		started = SharingStarted.Eagerly,
		// replay = 0 (default)
	)

	delay(500)

	launch {
		sharedFlow.collect { println("#1 $it") }
	}

	delay(1000)

	launch {
		sharedFlow.collect { println("#2 $it") }
	}

	delay(1000)

	launch {
		sharedFlow.collect { println("#3 $it") }
	}
}
// (1 sec)
// #1 A
// (1 sec)
// #1 B
// #2 B
// (1 sec)
// #1 C
// #2 C
// #3 C
```

Функция `shareIn` создает `SharedFlow` и отправляет элементы из этого `Flow`. Поскольку нам нужно начать корутину для сбора элементов в потоке, `shareIn` ожидает область корутин в качестве первого аргумента. Третий аргумент - `replay`, по умолчанию равен 0. Но наибольший интерес вызывает второй аргумент: `started` определяет, когда начинается прослушивание значений, в зависимости от количества слушателей. Поддерживаются следующие варианты:

•`SharingStarted.Eagerly` - сразу начинает прослушивание значений и отправляет их в поток. Обратите внимание, что если у вас есть ограниченное значение `replay`, и ваши значения появляются до начала подписки, вы можете потерять некоторые из них (если replay равен 0, вы потеряете все такие значения).

```kotlin
suspend fun main(): Unit = coroutineScope {
	val flow = flowOf("A", "B", "C")

	val sharedFlow: SharedFlow<String> = flow.shareIn(
		scope = this,
		started = SharingStarted.Eagerly,
	)

	delay(100)
	launch {
		sharedFlow.collect { println("#1 $it") }
	}
	print("Done")
}
// (0.1 sec)
// Done
```

• `SharingStarted.Lazily` - начинает прослушивание, когда появляется первый подписчик. Это гарантирует, что этот первый подписчик получит все отправленные значения, в то время как последующим подписчикам гарантируется получение только самых последних значений для повтора. Исходный поток продолжает работать даже тогда, когда все подписчики исчезли, но кэшируются только самые последние значения для повтора без подписчиков.

```kotlin
suspend fun main(): Unit = coroutineScope {
	val flow1 = flowOf("A", "B", "C")
	val flow2 = flowOf("D")
		.onEach { delay(1000) }

	val sharedFlow = merge(flow1, flow2).shareIn(
		scope = this,
		started = SharingStarted.Lazily,
	)

	delay(100)
	launch {
		sharedFlow.collect { println("#1 $it") }
	}
	delay(1000)
	launch {
		sharedFlow.collect { println("#2 $it") }
	}
}
// (0.1 sec)
// #1 A
// #1 B
// #1 C
// (1 sec)
// #2 D
// #1 D
```

• `WhileSubscribed() `- начинает прослушивание потока при появлении первого подписчика; прекращает прослушивание, когда исчезает последний подписчик. Если новый подписчик появляется, когда `SharedFlow` остановлен, он будет запущен заново. У `WhileSubscribed` есть дополнительные необязательные параметры конфигурации: `stopTimeoutMillis` (время прослушивания после исчезновения последнего подписчика, по умолчанию 0) и `replayExpirationMillis` (время, в течение которого хранится повтор после остановки, по умолчанию `Long.MAX_VALUE`).

```kotlin
suspend fun main(): Unit = coroutineScope {
	val flow = flowOf("A", "B", "C", "D")
		.onStart { println("Started") }
		.onCompletion { println("Finished") }
		.onEach { delay(1000) }

	val sharedFlow = flow.shareIn(
		scope = this,
		started = SharingStarted.WhileSubscribed(),
	)

	delay(3000)
	launch {
		println("#1 ${sharedFlow.first()}")
	}
	launch {
		println("#2 ${sharedFlow.take(2).toList()}")
	}
	delay(3000)
	launch {
		println("#3 ${sharedFlow.first()}")
	}
}
// (3 sec)
// Started
// (1 sec)
// #1 A
// (1 sec)
// #2 [A, B]
// Finished
// (1 sec)
// Started
// (1 sec)
// #3 A
// Finished
```

• Также возможно определить собственную стратегию, реализовав интерфейс `SharingStarted`.

Использование `shareIn` очень удобно, когда несколько сервисов заинтересованы в одних и тех же изменениях. Предположим, вам необходимо отслеживать изменения сохраненных местоположений со временем. Вот как может быть реализован объект передачи данных (DTO - Data Transfer Object) на Android с использованием библиотеки Room:

```kotlin
@Dao
interface LocationDao {
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertLocation(location: Location)

	@Query("DELETE FROM location_table")
	suspend fun deleteLocations()

	@Query("SELECT * FROM location_table ORDER BY time")
	fun observeLocations(): Flow<List<Location>>
}
```

Проблема заключается в том, что если несколько сервисов зависят от этих местоположений, то для каждого из них отдельно прослеживать базу данных не будет оптимальным. Вместо этого мы можем создать сервис, который слушает эти изменения и передает их в `SharedFlow`. Вот где мы будем использовать `shareIn`. Но как его настроить? Решение остается за вами. Хотите ли вы, чтобы ваши подписчики сразу получали последний список местоположений? Если да, установите `replay` на 1. Если вы хотите реагировать только на изменения, установите его на 0. А что касается `started`? В данном случае для этой цели лучше всего подходит` WhileSubscribed()`.

```kotlin
class LocationService(
	locationDao: LocationDao,
	scope: CoroutineScope
) {
	private val locations = locationDao.observeLocations()
		.shareIn(
			scope = scope,
			started = SharingStarted.WhileSubscribed(),
		)

	fun observeLocations(): Flow<List<Location>> = locations

}
```

Остерегайтесь! Не создавайте новый `SharedFlow` для каждого вызова. Создайте один и сохраните его в свойстве.

## StateFlow

348

