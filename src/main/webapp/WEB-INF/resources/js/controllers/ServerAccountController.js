'use strict';

/**
 * ServerAccountController
 * @constructor
 */

function ServerAccountController($rootScope, $window, $scope, $location, $modal, $routeParams, serverAccountFactory, serverTransactionsFactory) {
	
	$scope.trust = {
		'0':'No-Trust',
		'1':'Hyprid-Trust',
		'2':'Full-Trust'
	};
	$scope.Split = function(string, nb) {
		$scope.array = string.split("BTC");
		return $scope.result = $scope.array[nb];
	};

	$scope.balanceSplit = function(balanceBTC){
		if(balanceBTC != undefined){
			var balance = $scope.Split(balanceBTC, 0);
			return balance;
		}
	};
	
	
	loadRemoteData();
	
	function loadRemoteData(){
		$rootScope.initialized = true;
		serverAccountFactory.getServerAccountData($routeParams.serverId).then(function(data){
			$scope.serverAccount = data.serverAccountObject;
			$scope.serverAccount.activeBalance = $scope.balanceSplit(data.serverAccountObject.activeBalance);			
			$scope.serverAccount.balanceLimit = $scope.balanceSplit(data.serverAccountObject.balanceLimit);			
			$scope.serverAccount.userBalanceLimit = $scope.balanceSplit(data.serverAccountObject.userBalanceLimit);			
			$scope.lastTransactions = data.getHistoryTransferObject.transactionHistory;
		});
	}
	
	$scope.openDeleteModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalDelete.html',
			controller: ModalDeleteController,
			resolve: {
				modalTrust: function() {
					return $scope.serverAccount.trustLevel;
				},
				modalActiveBalance: function() {
					return $scope.serverAccount.activeBalance;
				}
			}
		});
		
		modalInstance.result.then(function(deleted){			
			if(deleted){
				var url = $scope.serverAccount.url;
				serverAccountFactory.deletedAccount(url).then(function(){
					 $window.history.back();
				});
			}
		});
	};
	
	$scope.openChangeRelationModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalChangeRelation.html',
			controller: ModalChangeRelationController,
			resolve: {
				modalLevel: function (){
					return $scope.serverAccount.trustLevel;
				}
			}
		});
		
		modalInstance.result.then(function(trustLevel){
			serverAccountFactory.updateTrustLevel($scope.serverAccount, trustLevel).then(function(data){
				
			});
		});
	};
	
	$scope.openBalanceLimitModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalBalanceLimit.html',
			controller: ModalBalanceLimitController,
			resolve: {
				modalLimit: function() {
					return $scope.serverAccount.balanceLimit;
				},
				modalUserLimit: function() {
					return $scope.serverAccount.userBalanceLimit;
				}
			}
		});
		
		modalInstance.result.then(function(balanceLimit){
			if($scope.serverAccount.trustLevel > 0){
				serverAccountFactory.updateBalanceLimit($scope.serverAccount, balanceLimit+"BTC").then(function(){
				});
			}
		});
	};

	$scope.openUserBalanceLimitModal = function () {
		
		var modalInstance = $modal.open({
			templateUrl: 'modalUserBalanceLimit.html',
			controller: ModalUserBalanceLimitController,
			resolve: {
				modalLimit: function() {
					return $scope.serverAccount.balanceLimit;
				},
				modalUserLimit: function() {
					return $scope.serverAccount.userBalanceLimit;
				}
			}
		});
		
		modalInstance.result.then(function(userBalanceLimit){
			if($scope.serverAccount.trustLevel > 0){				
				serverAccountFactory.updateUserBalanceLimit($scope.serverAccount, userBalanceLimit+"BTC").then(function(){
					
				});
			}
		});
	};
	
	$scope.openPayOutModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalPayout.html',
			controller: ModalPayoutAmountController
		});
		
		modalInstance.result.then(function(){
			
		});
	};

	$scope.openPayOutRuleModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalPayoutRule.html',
			controller: ModalPayoutRuleController
		});
		
		modalInstance.result.then(function(){
			
		});
	};
};

var ModalDeleteController = function ($scope, $modalInstance, modalTrust, modalActiveBalance) {
	
	$scope.trust = modalTrust;
	$scope.balance = modalActiveBalance;
	$scope.deleted = false;
	
	$scope.submit = function() {
		$scope.resetError();
		if($scope.trust == 0 && $scope.balance == 0){			
			$scope.deleted = true;			
			$modalInstance.close($scope.deleted);
		} else {
			$scope.setError("Please verify that Trust level is 'Non-Trust' and the active balance is '0'");
		}
	};

	$scope.cancel = function () {
		$scope.resetError();
		$modalInstance.dismiss('cancel');	
	};
	
    $scope.resetError = function() {
        $scope.error = false;
        $scope.errorMessage = '';
    };

    $scope.setError = function(message) {
        $scope.error = true;
        $scope.errorMessage = message;
    };
};

var ModalChangeRelationController = function ($scope, $modalInstance, modalLevel) {
	$scope.beforeTrust = modalLevel;
	$scope.levelOptions = [{'name': 'No-Trust', "value":0},{'name': 'Hyprid-Trust', "value":1},{'name': 'Full-Trust', "value":2}];
	
	$scope.submit = function() {
		$scope.resetError();
		if($scope.beforeTrust != $scope.selected){
			$modalInstance.close($scope.selected);
		}
	};

	$scope.selectValue = function(selected){
		$scope.selected = selected;
	};
	
	$scope.cancel = function () {
		$scope.resetError();
		$modalInstance.dismiss('cancel');	
	};
	
    $scope.resetError = function() {
        $scope.error = false;
        $scope.errorMessage = '';
    };

    $scope.setError = function(message) {
        $scope.error = true;
        $scope.errorMessage = message;
    };
};

var ModalBalanceLimitController = function ($scope, $modalInstance, modalLimit, modalUserLimit) {
	
	$scope.beforeLimit = modalLimit;
	$scope.userLimit = modalUserLimit;
	$scope.submit = function() {
		$scope.resetError();
		if($scope.balanceLimit < $scope.userLimit){
			$scope.setError("The balance limit cannot be less then the user balance limit!");
		} else if($scope.balanceLimit != $scope.beforeLimit && $scope.balanceLimit > 0){
			$scope.balanceLimit = $scope.balanceLimit.toPrecision(8);
			$modalInstance.close($scope.balanceLimit);
		}
	};

	$scope.setBalance = function(balance){
		$scope.balanceLimit = balance;
	};
	
	$scope.cancel = function () {
		$scope.resetError();
		$modalInstance.dismiss('cancel');	
	};
	
    $scope.resetError = function() {
        $scope.error = false;
        $scope.errorMessage = '';
    };

    $scope.setError = function(message) {
        $scope.error = true;
        $scope.errorMessage = message;
    };
};

var ModalUserBalanceLimitController = function ($scope, $modalInstance, modalLimit, modalUserLimit) {
	
	$scope.beforeBalanceLimit = modalUserLimit;
	$scope.balanceLimit = modalLimit;
	
	$scope.submit = function() {
		$scope.resetError();
		if( $scope.balanceLimit < $scope.userBalanceLimit){
			$scope.setError("The user balance limit cannot be greater than the balance limit!");
		} else if($scope.userBalanceLimit != $scope.beforeBalanceLimit && $scope.userBalanceLimit > 0){
			$scope.userBalanceLimit = $scope.userBalanceLimit.toPrecision(8);
			$modalInstance.close($scope.userBalanceLimit);
		}
	};
	
	$scope.setBalance = function(balance){
		$scope.userBalanceLimit = balance;
	};
	
	$scope.cancel = function () {
		$scope.resetError();
		$modalInstance.dismiss('cancel');	
	};
	
	$scope.resetError = function() {
		$scope.error = false;
		$scope.errorMessage = '';
	};
	
	$scope.setError = function(message) {
		$scope.error = true;
		$scope.errorMessage = message;
	};
};

var ModalPayoutAmountController = function ($scope, $modalInstance) {
	
	$scope.submit = function() {
		$scope.resetError();
		$modalInstance.close();
	};

	$scope.cancel = function () {
		$scope.resetError();
		$modalInstance.dismiss('cancel');	
	};
	
    $scope.resetError = function() {
        $scope.error = false;
        $scope.errorMessage = '';
    };

    $scope.setError = function(message) {
        $scope.error = true;
        $scope.errorMessage = message;
    };
};

var ModalPayoutRuleController = function ($scope, $modalInstance) {
	
	$scope.mytime = new Date();
	
	$scope.hstep = 1;
	$scope.dstep = 0;
	$scope.days = {
			'0':'Sunday',
			'1':'Monday',
			'2':'Tuesday',
			'3':'Wednesday',
			'4':'Thursday',
			'5':'Friday',
			'6':'Saturday'
		};
	
	$scope.options = {
			hstep: [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23],
			dstep: [0,1,2,3,4,5,6]
	};
	
	$scope.ismeridian = true;
	$scope.toggleMode = function(){
		$scope.ismeridian = ! $scope.ismeridian;
	};
	
	$scope.update = function() {
		var d = new Date();
		d.setHours(14);
	};
	
	$scope.changed = function(){
		console.log("Time changed to: " + $scope.mytime);
	};
	
	$scope.clear = function(){
		$scope.mytime = null;
	};
	
	$scope.submit = function() {
		$scope.resetError();
		$modalInstance.close();
	};

	$scope.cancel = function () {
		$scope.resetError();
		$modalInstance.dismiss('cancel');	
	};
	
    $scope.resetError = function() {
        $scope.error = false;
        $scope.errorMessage = '';
    };

    $scope.setError = function(message) {
        $scope.error = true;
        $scope.errorMessage = message;
    };
};