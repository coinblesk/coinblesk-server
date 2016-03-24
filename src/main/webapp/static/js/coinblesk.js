/**
 * After document loaded: set active class given the variable.
 */
$(function(){
	if (current_admin_menu) {
		adminMenu_setActive(current_admin_menu);
	}
}); 

/**
 * Sets the active class of an admin menu item. Removes the active class of the other menu items.
 * @param menuId 
 */
function adminMenu_setActive(menuId) {
   $("#admin_menu > li").each(function() {
	   $("#admin_menu_overview").removeClass("active"); 
   });
   $(menuId).addClass("active"); 
};

$("#admin_task_remove_burned").click(function() {
	$.get("remove-burned", function(data) {
		$("#admin_task_output").text(data);
	});
});

