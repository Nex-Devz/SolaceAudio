rootProject.name = "SolaceAudio"
include(":solaceaudio-plugin")
project(":solaceaudio-plugin").projectDir = file("plugin")
include(":solaceaudio-main")
project(":solaceaudio-main").projectDir = file("main")
