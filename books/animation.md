
```kotlin
        AnimatedContent(
            targetState = ch.value,
            transitionSpec = {
                val time = 400
                //Появление
                (fadeIn(animationSpec = tween(time / 2)) + expandVertically(
                    animationSpec = tween(
                        time
                    )
                ))
                    .togetherWith(
                        (fadeOut(animationSpec = tween(time)) + shrinkVertically(
                            animationSpec = tween(time)
                        ))
                    ).using(
                        SizeTransform(
                            clip = true,
                            sizeAnimationSpec = { _, _ -> tween(time) })
                    )
            }, label = ""
        )
        {
            if (it)
                CardImpulse(str)
            else
                CardCarrier(str)
        }
```


