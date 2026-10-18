package org.jisr.app.sandbox.js.polyfill

internal fun fetchPolyfill(): String = """
        function __normalize_fetch_headers(headers) {
            var out = {};
            if (!headers) return out;
            if (typeof headers.forEach === 'function') {
                headers.forEach(function(value, key) { out[key] = String(value); });
                return out;
            }
            if (Array.isArray(headers)) {
                headers.forEach(function(pair) {
                    if (pair && pair.length >= 2) out[pair[0]] = String(pair[1]);
                });
                return out;
            }
            Object.keys(headers).forEach(function(key) { out[key] = String(headers[key]); });
            return out;
        }

        function __fetch_bytes_to_base64(bytes) {
            var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
            var out = '';
            for (var i = 0; i < bytes.length; i += 3) {
                var a = bytes[i];
                var hasB = i + 1 < bytes.length;
                var hasC = i + 2 < bytes.length;
                var b = hasB ? bytes[i + 1] : 0;
                var c = hasC ? bytes[i + 2] : 0;
                out += chars.charAt(a >> 2);
                out += chars.charAt(((a & 3) << 4) | (b >> 4));
                out += hasB ? chars.charAt(((b & 15) << 2) | (c >> 6)) : '=';
                out += hasC ? chars.charAt(c & 63) : '=';
            }
            return out;
        }

        function __fetch_base64_to_bytes(value) {
            var binary = atob(value || '');
            var bytes = new Uint8Array(binary.length);
            for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            return bytes;
        }

        function __normalize_fetch_body(body) {
            if (body === undefined || body === null) return { kind: 'none', value: '' };
            if (typeof body === 'string') return { kind: 'text', value: body };

            var bytes = null;
            if (typeof ArrayBuffer !== 'undefined' && body instanceof ArrayBuffer) {
                bytes = new Uint8Array(body);
            } else if (typeof ArrayBuffer !== 'undefined' &&
                       typeof ArrayBuffer.isView === 'function' && ArrayBuffer.isView(body)) {
                bytes = new Uint8Array(body.buffer, body.byteOffset, body.byteLength);
            }
            if (bytes !== null) {
                return { kind: 'base64', value: __fetch_bytes_to_base64(bytes) };
            }
            return { kind: 'text', value: String(body) };
        }

        var fetch = async function(url, options) {
            options = options || {};
            var method = (options.method || 'GET').toUpperCase();
            var headers = __normalize_fetch_headers(options.headers);
            var body = __normalize_fetch_body(options.body);
            var followRedirects = options.redirect !== 'manual';
            var result = await __native_fetch(url, method, JSON.stringify(headers), body.kind, body.value, followRedirects);
            var parsed = JSON.parse(result);
            var responseBytes = null;
            function __response_bytes() {
                if (responseBytes === null) responseBytes = __fetch_base64_to_bytes(parsed.bodyBase64);
                return responseBytes;
            }
            return {
                ok: parsed.ok,
                status: parsed.status,
                statusText: parsed.statusText,
                url: parsed.url,
                headers: {
                    get: function(name) {
                        return parsed.headers[name.toLowerCase()] || null;
                    }
                },
                arrayBuffer: function() {
                    var bytes = __response_bytes();
                    var copy = new Uint8Array(bytes.length);
                    copy.set(bytes);
                    return Promise.resolve(copy.buffer);
                },
                text: function() { return Promise.resolve(parsed.body); },
                json: function() {
                    try {
                        if (parsed.body === null || parsed.body === undefined || parsed.body === '') {
                            return Promise.resolve(null);
                        }
                        return Promise.resolve(JSON.parse(parsed.body));
                    } catch (e) {
                        return Promise.resolve(null);
                    }
                }
            };
        };
""".trimIndent()

internal fun abortControllerPolyfill(): String = """
        if (typeof AbortSignal === 'undefined') {
            var AbortSignal = function() { this.aborted = false; this.reason = undefined; this._listeners = []; };
            AbortSignal.prototype.addEventListener = function(type, listener) {
                if (type !== 'abort' || typeof listener !== 'function') return;
                this._listeners.push(listener);
            };
            AbortSignal.prototype.removeEventListener = function(type, listener) {
                if (type !== 'abort') return;
                this._listeners = this._listeners.filter(function(l) { return l !== listener; });
            };
            AbortSignal.prototype.dispatchEvent = function(event) {
                if (!event || event.type !== 'abort') return true;
                for (var i = 0; i < this._listeners.length; i++) {
                    try { this._listeners[i].call(this, event); } catch (e) {}
                }
                return true;
            };
            globalThis.AbortSignal = AbortSignal;
        }

        if (typeof AbortController === 'undefined') {
            var AbortController = function() { this.signal = new AbortSignal(); };
            AbortController.prototype.abort = function(reason) {
                if (this.signal.aborted) return;
                this.signal.aborted = true;
                this.signal.reason = reason;
                this.signal.dispatchEvent({ type: 'abort' });
            };
            globalThis.AbortController = AbortController;
        }
""".trimIndent()
