'use strict';

/**
 * HomeController
 * @constructor
 */
var HomeController = function($scope, $http, $location, $rootScope, $templateCache) {
	$scope.home = {};
	$scope.editMode = false;
	
	$scope.fetchBalanceSum = function() {
		$http({'method': 'GET', 'url': 'home/balance', cache: $templateCache}).success(function(data, status, headers, config){
			$scope.data = data;
			$scope.status = status;
			console.log("Success", data);
		}).error(function(data, status, headers, config){
			$scope.data = data || 'Request failed';
			$scope.status = 'error';
			console.log("error", data);
		});
		
	};
	
	$scope.fetchBalanceSum();
};