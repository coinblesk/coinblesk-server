'use strict';

/**
 * LoginController
 * @constructor
 */

function LoginController($rootScope, $scope, $location, userAccountFactory) {
	
	$scope.login = function (credentials) {
    	$scope.credentials = {
    		    username: '',
    		    password: ''
    	};
    	
    	if(credentials.username == '' || credentials.password == '') {
    		//show error
    	}else {    	
    		$scope.$emit('event:loginRequest', credentials);
    	}
    };
};