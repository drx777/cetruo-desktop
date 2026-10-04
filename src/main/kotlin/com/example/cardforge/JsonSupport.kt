package com.example.cardforge

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object JsonSupport {
    val mapper: ObjectMapper = ObjectMapper().registerKotlinModule()
}
