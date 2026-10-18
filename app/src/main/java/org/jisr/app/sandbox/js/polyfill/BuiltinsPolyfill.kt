package org.jisr.app.sandbox.js.polyfill

internal fun arrayPolyfill(): String = """
        if (!Array.prototype.flat) {
            Array.prototype.flat = function(depth) {
                depth = depth === undefined ? 1 : Math.floor(depth);
                if (depth < 1) return Array.prototype.slice.call(this);
                return (function flatten(arr, d) {
                    return d > 0
                        ? arr.reduce(function(acc, val) { return acc.concat(Array.isArray(val) ? flatten(val, d - 1) : val); }, [])
                        : arr.slice();
                })(this, depth);
            };
        }

        if (!Array.prototype.flatMap) {
            Array.prototype.flatMap = function(callback, thisArg) { return this.map(callback, thisArg).flat(); };
        }
""".trimIndent()

internal fun objectPolyfill(): String = """
        if (!Object.entries) {
            Object.entries = function(obj) {
                var result = [];
                for (var key in obj) {
                    if (obj.hasOwnProperty(key)) result.push([key, obj[key]]);
                }
                return result;
            };
        }

        if (!Object.fromEntries) {
            Object.fromEntries = function(entries) {
                var result = {};
                for (var i = 0; i < entries.length; i++) {
                    result[entries[i][0]] = entries[i][1];
                }
                return result;
            };
        }
""".trimIndent()

internal fun stringPolyfill(): String = """
        if (!String.prototype.replaceAll) {
            String.prototype.replaceAll = function(search, replace) {
                if (search instanceof RegExp) {
                    if (!search.global) throw new TypeError('replaceAll must be called with a global RegExp');
                    return this.replace(search, replace);
                }
                return this.split(search).join(replace);
            };
        }
""".trimIndent()
