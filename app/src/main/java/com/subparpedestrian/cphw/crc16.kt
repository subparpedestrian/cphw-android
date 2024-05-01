package com.subparpedestrian.cphw

fun swapEveryOtherByte(byteArray: ByteArray): ByteArray {
    // Loop through the bytearray two bytes at a time
    for (i in 0 until byteArray.size - 1 step 2) {
        // Swap bytes
        val temp = byteArray[i]
        byteArray[i] = byteArray[i + 1]
        byteArray[i + 1] = temp
    }
    return byteArray
}

fun crc16(data: ByteArray): ByteArray {
    var crc = 0
    var out = 0
    var bitFlag: Int
    var thisByte: Int
    var bitsRead = 0
    var index = 0

    val newData = swapEveryOtherByte(data.copyOf())
    var byteLen = newData.size

    while (byteLen > 0) {
        bitFlag = out shr 15
        out = (out shl 1) and 0xFFFF

        thisByte = newData[index].toInt() and 0xFF

        // Get the next bit from thisByte
        out = out or ((thisByte shr bitsRead) and 1)

        bitsRead += 1
        if (bitsRead > 7) {
            bitsRead = 0
            index += 1
            byteLen -= 1
        }

        // Apply the CRC polynomial if the shifted-out bit is 1
        if (bitFlag != 0) {
            out = out xor 0x8005
        }
    }

    // Push out the last 16 bits
    for (i in 0 until 16) {
        bitFlag = out shr 15
        out = (out shl 1) and 0xFFFF
        if (bitFlag != 0) {
            out = out xor 0x8005
        }
    }

    // Reverse the bits in the CRC
    for (i in 0 until 16) {
        if (out and (1 shl i) != 0) {
            crc = crc or (1 shl (15 - i))
        }
    }

    return byteArrayOf((crc and 0xFF).toByte(), (crc shr 8).toByte())
//    return Pair(crc and 0xFF, crc shr 8)
}