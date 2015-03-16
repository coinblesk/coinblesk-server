'use strict';

/**
 * UsersController
 * @constructor
 */
var UsersController = function($scope, $location, $rootScope, userAccountFactory) {
	$scope.users = {};
	$scope.editMode = false;
	$scope.allUsers = [];
	
	loadRemoteData();
	
	function loadRemoteData(){
		userAccountFactory.getUsers()
		.then(
				function(users){
					$scope.allUsers = users; 
				});
	}
};