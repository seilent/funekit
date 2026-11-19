package net.seilent.funekit.api.engine

enum class Output { System, BitPerfect, Usb }

data class Dac(val vendor: Int, val product: Int, val serial: String)

data class Format(val rate: Int, val bits: Int, val channels: Int)
