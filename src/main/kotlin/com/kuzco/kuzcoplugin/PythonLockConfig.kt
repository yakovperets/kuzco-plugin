package com.kuzco.kuzcoplugin

import com.fasterxml.jackson.annotation.JsonProperty

data class PythonLockConfig(
    @JsonProperty("python_scripts_location")
    val pythonScriptsLocation: List<String>
)