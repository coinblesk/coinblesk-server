'use strict';

/**
 * LoginController
 * @constructor
 */

function LoginController($scope, $http, $location, $rootScope) {
    $scope.login = function (credentials) {
    	
    	$scope.credentials = {
    		    username: '',
    		    password: ''
    	};
    	
		$scope.$emit('event:loginRequest', credentials);
		$location.path('/');
    };
};