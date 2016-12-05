/* login: bob@seebi.ch password: longenoughpassword */
INSERT INTO USER_ACCOUNT ( ID, CREATION_DATE, BALANCE, DELETED, EMAIL, PASSWORD, USER_ROLE ) VALUES ( 1, CURRENT_TIMESTAMP(), 1.0, FALSE, 'bob@seebi.ch', '$2a$10$WFQFVa1tphKzgeOWbCVzb.OWCZqmcCNjUZ4G1pJaXY4hFi1o7YmoS', 'USER' );

/* login: anna@seebi.ch password: longenoughpassword */
INSERT INTO USER_ACCOUNT ( ID, CREATION_DATE, BALANCE, DELETED, EMAIL, PASSWORD, USER_ROLE ) VALUES ( 2, CURRENT_TIMESTAMP(), 2.0, FALSE, 'anna@seebi.ch', '$2a$10$WFQFVa1tphKzgeOWbCVzb.OWCZqmcCNjUZ4G1pJaXY4hFi1o7YmoS', 'USER' );
