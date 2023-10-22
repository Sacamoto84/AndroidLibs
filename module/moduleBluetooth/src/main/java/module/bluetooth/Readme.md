```kotlin
decoder.run()
decoder.addCmd("pong") {
}
```


```mermaid
flowchart TD

subgraph BT
        chIn --> sendReceiveScope -- Raw строка -->channelIn
        name
        chOut --> createMessage --> bt.write
end


subgraph decode

    run --> decodeScope
    run --> commandDecoder
    run --> cliDecoder
    
    decodeScope
    commandDecoder
    cliDecoder
end


subgraph decodeScope
        channelIn -- Raw строка --> decodeScope
        channelRoute
end

subgraph commandDecoder
    channelRoute --> channelOutCommand
end

subgraph cliDecoder
    channelOutCommand --> parse --> r("Выполенение команды")
end

   

    
```

