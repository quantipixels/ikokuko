package com.quantipixels.ikokuko.sample

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform