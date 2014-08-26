'use strict';

/**
 * ServerAccountController
 * @constructor
 */

function ServerAccountController($rootScope, $scope, $location, $modal, $routeParams, serverAccountFactory, serverTransactionsFactory) {
	
	loadRemoteData();
	
	function loadRemoteData(){
		serverAccountFactory.getServerAccount($routeParams.serverId).then(function(account){
			$scope.serverAccount = account;
		});

		serverTransactionsFactory.getLastAccountTransactions($routeParams.serverId).then(function(transactions){
			$scope.lastTransactions = transactions.data;
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
			serverAccountFactory.updateTrustLevel($scope.serverAccount, trustLevel).then(function(){
				
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
	$scope.levelOptions.value = modalLevel;
	
	$scope.submit = function() {
		$scope.resetError();
		if($scope.beforeTrust != $scope.levelOptions.value){			
			$modalInstance.close($scope.levelOptions.value);
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