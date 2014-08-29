'use strict';

/**
 * LoginController
 * @constructor
 */

function LoginController($rootScope, $scope, $location, userAccountFactory) {
	
	var uri = $location.absUrl();
	var indexStart = uri.indexOf('//');
	var indexEnd = uri.indexOf("/#");
	//$rootScope.baseUrl = uri.substring(indexStart + 2, indexEnd);
	$rootScope.baseUrl = "bitcoin-clone2.csg.uzh.ch";
	
	$scope.login = function (credentials) {
    	$scope.credentials = {
    		    username: '',
    		    password: ''
    	};
    	
    	if(credentials.username == '' || credentials.password == '') {
    		//do nothing
    	}else {    	
    		var userName = credentials.username;
    		credentials.username = userName + "@" + $rootScope.baseUrl;
    		console.log(credentials);
    		$scope.$emit('event:loginRequest', credentials);
    		$location.path('/home');
    	}
    };
};