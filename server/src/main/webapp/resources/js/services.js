'use strict';

/* Services */

var AppServices = angular.module('SpringAngularApp.services', []);

AppServices.value('version', '0.1');

AppServices.service('accessTokenService', function($cookies, $cookieStore){
	return ({
		initToken: initToken,
		removeToken: removeToken,
		initCookies: initCookies
	});
	
	function initToken(token){
		httpHeaders.common['Authorization'] = token || $cookies.token;
	};
	
	function initCookies() {
		var jsessionid = $cookies.JSESSIONID;
		$cookieStore.put('JSESSIONID', jsessionid);
		console.log("Services " + jsessionid);
	};
	
	function removeToken() {
		httpHeaders.common['Authorization'] = null;
	};
	
	function removeCookies() {
		delete $cookie.JSESSIONID;
		$cookieStore.remove('JSESSIONID');
	};
});

AppServices.service('balanceService', function($http, $q) {
	return({
		getBalance: getBalance
	});
	
	function getBalance(){
		var request = $http({
			method: 'GET', 
			url: 'home/balance'
		});
		
		return( request.then(handleSuccess, handleError));
	}
		
	function handleSuccess( response ){
		return(response.data);
		console.log("Success", data);
	}
	
	function handleError( response ){
		if(!angular.isObject(response.data) || !response.data.message){
			return($q.reject("Request failed"));
		}
		
		return($q.reject(response.data));
	}
});

AppServices.factory('serverTransactionsFactory', function($http, $q) {
	var serverTransactionsFactory = {};
	
	serverTransactionsFactory.getLastThreeTransactions = function() {
		var request = $http({
			method: 'GET',
			url: 'home/lastThreeTransaction'
		});
		return (request.then(serverTransactionsFactory.handleSucess, serverTransactionsFactory.handleError));
	};
	
	serverTransactionsFactory.getHistory = function() {
		var request = $http({
			method: 'GET',
			url: "history/transactions"
		});
		return (request.then(serverTransactionsFactory.handleSuccess, serverTransactionsFactory.handleError));
	};
	
	serverTransactionsFactory.handleSuccess = function( response ){
		return(response.data);
		console.log("Success", data);
	};
	
	serverTransactionsFactory.handleError = function( response ){
		if(!angular.isObject(response.data) || !response.data.message){
			return($q.reject("Request failed"));
		}
		
		return($q.reject(response.data));
	};
	
	return serverTransactionsFactory;
});

AppServices.factory('userAccountFactory', function($http, $q) {
	var userAccountFactory = {};
	
	userAccountFactory.getAdmins = function() {
		var request = $http({
			method: 'GET',
			url: 'home/admins'
		});
		return (request.then(userAccountFactory.handleSuccess, userAccountFactory.handleError));
	};
	
	userAccountFactory.getUsers = function() {
		var request = $http({
			method: 'GET',
			url: 'users/all'
		});
		return (request.then(userAccountFactory.handleSuccess, userAccountFactory.handleError));
	};
	
	userAccountFactory.handleSuccess = function( response ){
		return(response.data);
		console.log("Success", data);
	};
	
	userAccountFactory.handleError = function( response ){
		if(!angular.isObject(response.data) || !response.data.message){
			return($q.reject("Request failed"));
		}
		
		return($q.reject(response.data));
	};
	
	return userAccountFactory;
});

AppServices.factory('serverAccountFactory', function($http, $q) {
	var serverAccountFactory = {};
	
	serverAccountFactory.getAccounts = function() {
		var request = $http({
			method: 'GET',
			url: 'relation/accounts'
		});
		return (request.then(serverAccountFactory.handleSuccess, serverAccountFactory.handleError));
	};
	
	serverAccountFactory.handleSuccess = function( response ){
		return(response.data);
		console.log("Success", data);
	};
	
	serverAccountFactory.handleError = function( response ){
		if(!angular.isObject(response.data) || !response.data.message){
			return($q.reject("Request failed"));
		}
		
		return($q.reject(response.data));
	};
	
	return serverAccountFactory;
});

//TODO: mehmet set link http://wemadeyoulook.at/en/blog/implementing-basic-http-authentication-http-requests-angular/
AppServices.factory('base64Factory', function() {
	
	var base64Factory = {};
	
	var keyStr = 'ABCDEFGHIJKLMNOP' +
        'QRSTUVWXYZabcdef' +
        'ghijklmnopqrstuv' +
        'wxyz0123456789+/' +
        '=';
    
	base64Factory.encode = function (input) {
        var output = "";
        var chr1, chr2, chr3 = "";
        var enc1, enc2, enc3, enc4 = "";
        var i = 0;

        do {
            chr1 = input.charCodeAt(i++);
            chr2 = input.charCodeAt(i++);
            chr3 = input.charCodeAt(i++);

            enc1 = chr1 >> 2;
            enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
            enc3 = ((chr2 & 15) << 2) | (chr3 >> 6);
            enc4 = chr3 & 63;

            if (isNaN(chr2)) {
                enc3 = enc4 = 64;
            } else if (isNaN(chr3)) {
                enc4 = 64;
            }

            output = output +
                keyStr.charAt(enc1) +
                keyStr.charAt(enc2) +
                keyStr.charAt(enc3) +
                keyStr.charAt(enc4);
            chr1 = chr2 = chr3 = "";
            enc1 = enc2 = enc3 = enc4 = "";
        } while (i < input.length);

        return output;
	};
	
	base64Factory.decode = function (input) {
        var output = "";
        var chr1, chr2, chr3 = "";
        var enc1, enc2, enc3, enc4 = "";
        var i = 0;

        // remove all characters that are not A-Z, a-z, 0-9, +, /, or =
        var base64test = /[^A-Za-z0-9\+\/\=]/g;
        if (base64test.exec(input)) {
            alert("There were invalid base64 characters in the input text.\n" +
                "Valid base64 characters are A-Z, a-z, 0-9, '+', '/',and '='\n" +
                "Expect errors in decoding.");
        }
        input = input.replace(/[^A-Za-z0-9\+\/\=]/g, "");

        do {
            enc1 = keyStr.indexOf(input.charAt(i++));
            enc2 = keyStr.indexOf(input.charAt(i++));
            enc3 = keyStr.indexOf(input.charAt(i++));
            enc4 = keyStr.indexOf(input.charAt(i++));

            chr1 = (enc1 << 2) | (enc2 >> 4);
            chr2 = ((enc2 & 15) << 4) | (enc3 >> 2);
            chr3 = ((enc3 & 3) << 6) | enc4;

            output = output + String.fromCharCode(chr1);

            if (enc3 != 64) {
                output = output + String.fromCharCode(chr2);
            }
            if (enc4 != 64) {
                output = output + String.fromCharCode(chr3);
            }

            chr1 = chr2 = chr3 = "";
            enc1 = enc2 = enc3 = enc4 = "";

        } while (i < input.length);

        return output;
    };
    
    return base64Factory;
});