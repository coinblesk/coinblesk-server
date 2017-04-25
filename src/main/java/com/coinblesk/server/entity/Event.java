/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.coinblesk.server.entity;

import static javax.persistence.EnumType.STRING;
import static javax.persistence.GenerationType.AUTO;
import static javax.persistence.TemporalType.TIMESTAMP;
import static lombok.AccessLevel.NONE;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Temporal;

import com.coinblesk.server.enumerator.EventType;
import com.coinblesk.server.enumerator.EventUrgence;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@Entity(name = "EVENT")
public class Event implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = AUTO)
	@Setter(NONE)
	@Column(name = "ID", nullable = false)
	private long id;

	@Temporal(TIMESTAMP)
	@Column(name = "EVENT_DATE", nullable = false)
	private Date date;

	@Enumerated(STRING)
	@Column(name = "EVENT_TYPE", nullable = false)
	private EventType type;

	@Enumerated(STRING)
	@Column(name = "EVENT_URGENCE", nullable = false)
	private EventUrgence urgence;

	@Column(name = "EVENT_DESCRIPTION", nullable = true)
	private String description;

}
