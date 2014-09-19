'use strict';

/**
 * ServerAccountController
 * @constructor
 */

function ServerAccountController($rootScope, $scope, $location, $modal, $routeParams, serverAccountFactory, serverTransactionsFactory) {
	
	$scope.trust = {
		'0':'No-Trust',
		'1':'Hyprid-Trust',
		'2':'Full-Trust'
	};
	
	loadRemoteData();
	
	function loadRemoteData(){
		serverAccountFactory.getServerAccountData($routeParams.serverId).then(function(data){
			$scope.serverAccount = data.serverAccountObject;
			$scope.lastTransactions = data.getHistoryTransferObject.transactionHistory;
		});
	}
	
	$scope.openDeleteModal = function () {

		var modalInstance = $modal.open({
			templateUrl: 'modalDelete.html',
			controller: ModalDeleteController
		});
		
		modalInstance.result.then(function(deleted){			
			if(deleted){
				serverAccountFactory.deletedAccount($scope.serverAccount.url).then(function(success){
					
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
				}
			}
		});
		
		modalInstance.result.then(function(balanceLimit){		
			serverAccountFactory.updateBalanceLimit($scope.serverAccount, balanceLimit).then(function(){
					
			});
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
			serverAccountFactory.updateUserBalanceLimit($scope.serverAccount, userBalanceLimit).then(function(){
				
			});
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

var ModalDeleteController = function ($scope, $modalInstance) {
	
	$scope.deleted = false;
	
	$scope.submit = function() {
		$scope.resetError();
		$scope.deleted = true;
		$modalInstance.close($scope.deleted);
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

var ModalBalanceLimitController = function ($scope, $modalInstance, modalLimit) {
	
	$scope.beforeLimit = modalLimit;
	$scope.balanceLimit = modalLimit;
	$scope.submit = function() {
		$scope.resetError();
		if($scope.balanceLimit != $scope.beforeLimit && $scope.balanceLimit > 0){			
			$modalInstance.close($scope.balanceLimit);
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

var ModalUserBalanceLimitController = function ($scope, $modalInstance, modalLimit, modalUserLimit) {
	
	$scope.beforeLimit = modalLimit;
	$scope.userBalanceLimit = modalLimit;
	
	$scope.submit = function() {
		$scope.resetError();
		if($scope.userBalanceLimit != $scope.beforeLimit && $scope.userBalanceLimit > 0){			
			$modalInstance.close($scope.userBalanceLimit);
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