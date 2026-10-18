package org.jisr.app.sandbox.js.polyfill

internal fun urlPolyfill(): String = """
        var __native_parse_url = typeof __parse_url !== 'undefined' ? __parse_url : function(u) { return JSON.stringify({ protocol: '', host: '', hostname: '', port: '', pathname: '/', search: '', hash: '' }); };
        var URL = function(urlString, base) {
            var fullUrl = urlString;
            if (base && !/^https?:\/\//i.test(urlString)) {
                var b = typeof base === 'string' ? base : base.href;
                if (urlString.charAt(0) === '/') {
                    var m = b.match(/^(https?:\/\/[^\/]+)/);
                    fullUrl = m ? m[1] + urlString : urlString;
                } else {
                    fullUrl = b.replace(/\/[^\/]*$/, '/') + urlString;
                }
            }
            var parsed = __native_parse_url(fullUrl);
            var data = JSON.parse(parsed);
            this.href = fullUrl;
            this.protocol = data.protocol;
            this.host = data.host;
            this.hostname = data.hostname;
            this.port = data.port;
            this.pathname = data.pathname;
            this.search = data.search;
            this.hash = data.hash;
            this.origin = data.protocol + '//' + data.host;
            this.searchParams = new URLSearchParams(data.search || '');
        };
        URL.prototype.toString = function() { return this.href; };

        var URLSearchParams = function(init) {
            this._params = {};
            var self = this;
            if (init && typeof init === 'object' && !Array.isArray(init)) {
                Object.keys(init).forEach(function(key) { self._params[key] = String(init[key]); });
            } else if (typeof init === 'string') {
                init.replace(/^\?/, '').split('&').forEach(function(pair) {
                    var parts = pair.split('=');
                    if (parts[0]) self._params[decodeURIComponent(parts[0])] = decodeURIComponent(parts[1] || '');
                });
            }
        };
        URLSearchParams.prototype.toString = function() {
            var self = this;
            return Object.keys(this._params).map(function(key) {
                return encodeURIComponent(key) + '=' + encodeURIComponent(self._params[key]);
            }).join('&');
        };
        URLSearchParams.prototype.get = function(key) { return this._params.hasOwnProperty(key) ? this._params[key] : null; };
        URLSearchParams.prototype.set = function(key, value) { this._params[key] = String(value); };
        URLSearchParams.prototype.append = function(key, value) { this._params[key] = String(value); };
        URLSearchParams.prototype.has = function(key) { return this._params.hasOwnProperty(key); };
        URLSearchParams.prototype.delete = function(key) { delete this._params[key]; };
        URLSearchParams.prototype.keys = function() { return Object.keys(this._params); };
        URLSearchParams.prototype.values = function() {
            var self = this;
            return Object.keys(this._params).map(function(k) { return self._params[k]; });
        };
        URLSearchParams.prototype.entries = function() {
            var self = this;
            return Object.keys(this._params).map(function(k) { return [k, self._params[k]]; });
        };
        URLSearchParams.prototype.forEach = function(callback) {
            var self = this;
            Object.keys(this._params).forEach(function(key) { callback(self._params[key], key, self); });
        };
        URLSearchParams.prototype.getAll = function(key) {
            return this._params.hasOwnProperty(key) ? [this._params[key]] : [];
        };
        URLSearchParams.prototype.sort = function() {
            var sorted = {};
            var self = this;
            Object.keys(this._params).sort().forEach(function(k) { sorted[k] = self._params[k]; });
            this._params = sorted;
        };
""".trimIndent()
