'use strict';

/**
 * LogoutController
 * @constructor
 */

function LogoutController($scope, $location) {
	
	$scope.logout = function () {
		$scope.$emit('event:logoutRequest');
		$location.path('/');
    };
};