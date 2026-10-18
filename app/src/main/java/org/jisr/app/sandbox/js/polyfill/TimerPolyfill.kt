package org.jisr.app.sandbox.js.polyfill

internal fun timerPolyfill(): String = """
        var __plugin_timer_id = 0;
        var __plugin_timers = Object.create(null);
        function __plugin_delay_value(value, interval) {
            var duration = Number(value);
            if (!isFinite(duration) || duration < 0) duration = 0;
            duration = Math.min(Math.floor(duration), 60000);
            return interval && duration < 1 ? 1 : duration;
        }
        function __plugin_report_timer_error(error) {

        }
        if (typeof globalThis.setTimeout === 'undefined') {
            globalThis.setTimeout = function(callback, delay) {
                if (typeof callback !== 'function') throw new TypeError('Timer callback must be a function');
                var id = ++__plugin_timer_id;
                var args = Array.prototype.slice.call(arguments, 2);
                __plugin_timers[id] = true;
                __plugin_sleep(__plugin_delay_value(delay, false)).then(function() {
                    if (!__plugin_timers[id]) return;
                    delete __plugin_timers[id];
                    try { callback.apply(globalThis, args); } catch (error) { __plugin_report_timer_error(error); }
                }, function(error) {
                    delete __plugin_timers[id];
                    __plugin_report_timer_error(error);
                });
                return id;
            };
        }
        if (typeof globalThis.clearTimeout === 'undefined') {
            globalThis.clearTimeout = function(id) { delete __plugin_timers[id]; };
        }
        if (typeof globalThis.setInterval === 'undefined') {
            globalThis.setInterval = function(callback, delay) {
                if (typeof callback !== 'function') throw new TypeError('Timer callback must be a function');
                var id = ++__plugin_timer_id;
                var duration = __plugin_delay_value(delay, true);
                var args = Array.prototype.slice.call(arguments, 2);
                __plugin_timers[id] = true;
                function tick() {
                    __plugin_sleep(duration).then(function() {
                        if (!__plugin_timers[id]) return;
                        try { callback.apply(globalThis, args); } catch (error) { __plugin_report_timer_error(error); }
                        if (__plugin_timers[id]) tick();
                    }, function(error) {
                        delete __plugin_timers[id];
                        __plugin_report_timer_error(error);
                    });
                }
                tick();
                return id;
            };
        }
        if (typeof globalThis.clearInterval === 'undefined') {
            globalThis.clearInterval = function(id) { delete __plugin_timers[id]; };
        }
""".trimIndent()
