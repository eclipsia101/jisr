package org.jisr.app.sandbox.js.polyfill

internal fun requirePolyfill(): String = """
        var require = function(moduleName) {
            if (moduleName === 'cheerio' || moduleName === 'cheerio-without-node-native' || moduleName === 'react-native-cheerio') {
                return cheerio;
            }
            if (moduleName === 'crypto-js') {
                return CryptoJS;
            }
            throw new Error("Module '" + moduleName + "' is not available");
        };
""".trimIndent()
