'use strict';

/**
 * LogoutController
 * @constructor
 */

function LogoutController($rootScope,$scope, $location) {

	$scope.logout = function () {
		$scope.$emit('event:logoutRequest');
		$rootScope.initialized = false;
		$location.path('/');
    };
};