https://chercher.tech/kotlin/properties-file-kotlin

https://crunchify.com/java-properties-file-how-to-read-config-properties-values-in-java/

Чтение файла pathconfig-p.txt

```kotlin
time=Good Time
hour=Elevent Hour
```

```kotlin
// create file input stream object for the properties file
val fis = FileInputStream("C:pathconfig-p.txt") // create Properties class object to access properties file
val prop = Properties() // load the properties file
prop.load(fis) // get the property of "url" using getProperty()
println(prop.getProperty("time")) // get the property of "url" using get()
println(prop["hour"])
```