/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 ONOS GUI -- Remote -- General Functions - Unit Tests
 */
describe('factory: fw/remote/urlfn.js', function () {
    var $log, $loc, ufs, fs;

    var protocol, host, port;

    beforeEach(module('onosRemote'));

    beforeEach(module(function($provide) {
       $provide.factory('$location', function (){
        return {
            protocol: function () { return protocol; },
            host: function () { return host; },
            port: function () { return port; }
        };
       })
    }));

    beforeEach(inject(function (_$log_, $location, UrlFnService, FnService) {
        $log = _$log_;
        $loc = $location;
        ufs = UrlFnService;
        fs = FnService;
    }));

    function setLoc(prot, h, p) {
        protocol = prot;
        host = h;
        port = p;
    }

    it('should define UrlFnService', function () {
        expect(ufs).toBeDefined();
    });

    it('should define api functions', function () {
        expect(fs.areFunctions(ufs, [
            'rsUrl', 'wsUrl'
        ])).toBeTruthy();
    });

    it('should return the correct (http) RS url', function () {
        setLoc('http', 'foo', '123');
        expect(ufs.rsUrl('path')).toEqual('http://foo:123/onos/ui/rs/path');
    });

    it('should return the correct (https) RS url', function () {
        setLoc('https', 'foo', '123');
        expect(ufs.rsUrl('path')).toEqual('https://foo:123/onos/ui/rs/path');
    });

    it('should return the correct (ws) WS url', function () {
        setLoc('http', 'foo', '123');
        expect(ufs.wsUrl('path')).toEqual('ws://foo:123/onos/ui/ws/path');
    });

    it('should return the correct (wss) WS url', function () {
        setLoc('https', 'foo', '123');
        expect(ufs.wsUrl('path')).toEqual('wss://foo:123/onos/ui/ws/path');
    });

    it('should allow us to define an alternate WS port', function () {
        setLoc('http', 'foo', '123');
        expect(ufs.wsUrl('xyyzy', 456)).toEqual('ws://foo:456/onos/ui/ws/xyyzy');
    });
});
