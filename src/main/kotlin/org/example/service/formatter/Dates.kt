package org.example.org.example.service.formatter

import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun LocalDate.format(): String = this.format(DateTimeFormatter.ofPattern("MM/dd/yy"))
