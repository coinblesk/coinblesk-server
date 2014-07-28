'use strict';

/**
 * RelationController
 * @constructor
 */
var RelationController = function($scope, $location, $rootScope, serverAccountFactory) {
	$scope.trust = {};
	$scope.editMode = false;
	$scope.serveraccounts = [];
	
	loadRemoteData();
	
	function loadRemoteData(){
		serverAccountFactory.getAccounts()
		.then(
				function(accounts){
					$scope.serveraccounts = accounts; 
				});
	}
};