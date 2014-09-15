'use strict';

/**
 * ActivitiesController
 * @constructor
 */
var ActivitiesController = function($scope, $location, $rootScope, activitiesFactory) {
	
	loadRemoteData();
	
	function loadRemoteData(){
		activitiesFactory.getActivities().then(function(logs){
					$scope.activitylogs = logs.activitiesList; 
		});
	}
};