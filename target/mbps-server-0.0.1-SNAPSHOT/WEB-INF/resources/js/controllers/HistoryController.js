'use strict';

/**
 * HistoryController
 * @constructor
 */
var HistoryController = function($rootScope, $scope, $location, serverTransactionsFactory) {
	$scope.history = {};
	$scope.editMode = false;
	$scope.servertransactions = [];
	
	loadRemoteData();
	
	function loadRemoteData(){
		serverTransactionsFactory.getHistory()
		.then(function(transactions){
					$scope.servertransactions = transactions; 
		});
	}
};