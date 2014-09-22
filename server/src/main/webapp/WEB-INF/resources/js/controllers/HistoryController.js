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
		$rootScope.initialized = true;
		serverTransactionsFactory.getHistory()
		.then(function(transactions){
					$scope.servertransactions = transactions.transactionHistory; 
		});
	}
};