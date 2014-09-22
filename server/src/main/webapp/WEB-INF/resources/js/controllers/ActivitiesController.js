'use strict';

/**
 * ActivitiesController
 * @constructor
 */
var ActivitiesController = function($rootScope, $scope, $location, activitiesFactory) {
	loadRemoteData();
	
	function loadRemoteData(){
		$rootScope.initialized = true;
		activitiesFactory.getActivities().then(function(logs){
					$scope.activitylogs = logs.activitiesList; 
		});
	}
};