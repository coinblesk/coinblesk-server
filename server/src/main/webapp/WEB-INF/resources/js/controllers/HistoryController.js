'use strict';

/**
 * HistoryController
 * @constructor
 */
var HistoryController = function($rootScope, $scope, $location, serverTransactionsFactory) {
	$scope.history = {};
	$scope.editMode = false;
	$scope.servertransactions = [];
	$scope.Split = function(string, nb) {
		$scope.array = string.split("BTC");
		return $scope.result = $scope.array[nb];
	};

	$scope.amountSplit = function(balanceBTC){
		if(balanceBTC != undefined){
			var balance = $scope.Split(balanceBTC, 0);
			return balance;
		}
	};
	
	
	loadRemoteData();
	
	function loadRemoteData(){
		$rootScope.initialized = true;
		if($rootScope.loggedusername!=undefined || $rootScope.loggedusername.length > 4){			
			$scope.loggeduser = $rootScope.loggedusername;
		}
		serverTransactionsFactory.getHistory()
		.then(function(transactions){
					$scope.servertransactions = transactions.transactionHistory; 
		});
	}
};