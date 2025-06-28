package com.github.oscsta.runni

class App {
    val greeting: String
        get() = "Hello World!"
}

fun main() {
    println(App().greeting)
}
