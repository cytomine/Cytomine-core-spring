CREATE INDEX IF NOT EXISTS command_user_id_index ON command USING btree (user_id);
CREATE INDEX IF NOT EXISTS command_project_id_index ON command USING btree (project_id);
CREATE INDEX IF NOT EXISTS command_created_index ON command USING btree (created);
CREATE INDEX IF NOT EXISTS command_history_project_id_index ON command_history USING btree (project_id);
CREATE INDEX IF NOT EXISTS command_history_user_id_index ON command_history USING btree (user_id);
CREATE INDEX IF NOT EXISTS command_history_created_index ON command_history USING btree (created);
CREATE INDEX IF NOT EXISTS command_history_command_id_index ON command_history USING btree (command_id);
CREATE INDEX IF NOT EXISTS undo_stack_item_command_id_index ON undo_stack_item USING btree (command_id);
CREATE INDEX IF NOT EXISTS redo_stack_item_command_id_index ON redo_stack_item USING btree (command_id);
CREATE INDEX IF NOT EXISTS acl_object_identity_object_id_identity_index ON acl_object_identity USING btree (object_id_identity);
CREATE INDEX IF NOT EXISTS acl_entry_acl_object_identity_index ON acl_entry USING btree (acl_object_identity);
CREATE INDEX IF NOT EXISTS acl_entry_sid_index ON acl_entry USING btree (sid);
CREATE INDEX IF NOT EXISTS acl_sid_sid_index ON acl_sid USING btree (sid);
CREATE INDEX IF NOT EXISTS storage_user_id_index ON storage USING btree (user_id);
CREATE INDEX IF NOT EXISTS uploaded_file_user_id_index ON uploaded_file USING btree (user_id);
CREATE INDEX IF NOT EXISTS uploaded_file_storage_id_index ON uploaded_file USING btree (storage_id);
CREATE INDEX IF NOT EXISTS uploaded_file_parent_id_index ON uploaded_file USING btree (parent_id);
CREATE INDEX IF NOT EXISTS uploaded_file_l_tree_index ON uploaded_file USING GIST (l_tree);
CREATE INDEX IF NOT EXISTS sample_name_index ON sample USING btree (name);
CREATE INDEX IF NOT EXISTS sample_created_index ON sample USING btree (created);
CREATE INDEX IF NOT EXISTS abstract_image_created_index ON abstract_image USING btree (created);
CREATE INDEX IF NOT EXISTS abstract_image_sample_id_index ON abstract_image USING btree (sample_id);
CREATE INDEX IF NOT EXISTS abstract_image_uploaded_file_id_index ON abstract_image USING btree (uploaded_file_id);
CREATE INDEX IF NOT EXISTS abstract_image_user_id_index ON abstract_image USING btree (user_id);
CREATE INDEX IF NOT EXISTS abstract_slice_uploaded_file_id_index ON abstract_slice USING btree (uploaded_file_id);
CREATE INDEX IF NOT EXISTS abstract_slice_image_id_index ON abstract_slice USING btree (image_id);
CREATE INDEX IF NOT EXISTS image_instance_created_index ON image_instance USING btree (created);
CREATE INDEX IF NOT EXISTS image_instance_base_image_id_index ON image_instance USING btree (base_image_id);
CREATE INDEX IF NOT EXISTS image_instance_project_id_index ON image_instance USING btree (project_id);
CREATE INDEX IF NOT EXISTS image_instance_user_id_index ON image_instance USING btree (user_id);
CREATE INDEX IF NOT EXISTS slice_instance_base_slice_id_index ON slice_instance USING btree (base_slice_id);
CREATE INDEX IF NOT EXISTS property_domain_ident_index ON property USING btree (domain_ident);
CREATE INDEX IF NOT EXISTS property_key_index ON property USING btree (key);
CREATE INDEX IF NOT EXISTS attached_file_domain_ident_index ON attached_file USING btree (domain_ident);
CREATE INDEX IF NOT EXISTS description_domain_ident_index ON description USING btree (domain_ident);
CREATE INDEX IF NOT EXISTS annotation_index_slice_id_index ON annotation_index USING btree (slice_id);
CREATE INDEX IF NOT EXISTS annotation_index_user_id_index ON annotation_index USING btree (user_id);
CREATE INDEX IF NOT EXISTS user_annotation_image_id_index ON user_annotation USING btree (image_id);
CREATE INDEX IF NOT EXISTS user_annotation_slice_id_index ON user_annotation USING btree (slice_id);
CREATE INDEX IF NOT EXISTS user_annotation_user_id_index ON user_annotation USING btree (user_id);
CREATE INDEX IF NOT EXISTS user_annotation_created_index ON user_annotation USING btree (created);
CREATE INDEX IF NOT EXISTS user_annotation_project_id_index ON user_annotation USING btree (project_id);
CREATE INDEX IF NOT EXISTS user_annotation_location_index ON user_annotation USING GIST (location);
CREATE INDEX IF NOT EXISTS algo_annotation_image_id_index ON algo_annotation USING btree (image_id);
CREATE INDEX IF NOT EXISTS algo_annotation_slice_id_index ON algo_annotation USING btree (slice_id);
CREATE INDEX IF NOT EXISTS algo_annotation_user_id_index ON algo_annotation USING btree (user_id);
CREATE INDEX IF NOT EXISTS algo_annotation_created_index ON algo_annotation USING btree (created);
CREATE INDEX IF NOT EXISTS algo_annotation_project_id_index ON algo_annotation USING btree (project_id);
CREATE INDEX IF NOT EXISTS algo_annotation_location_index ON algo_annotation USING GIST (location);
CREATE INDEX IF NOT EXISTS reviewed_annotation_project_id_index ON reviewed_annotation USING btree (project_id);
CREATE INDEX IF NOT EXISTS reviewed_annotation_user_id_index ON reviewed_annotation USING btree (user_id);
CREATE INDEX IF NOT EXISTS reviewed_annotation_image_id_index ON reviewed_annotation USING btree (image_id);
CREATE INDEX IF NOT EXISTS reviewed_annotation_slice_id_index ON reviewed_annotation USING btree (slice_id);
CREATE INDEX IF NOT EXISTS reviewed_annotation_location_index ON reviewed_annotation USING GIST (location);
CREATE INDEX IF NOT EXISTS annotation_term_user_annotation_id_index ON annotation_term USING btree (user_annotation_id);
CREATE INDEX IF NOT EXISTS annotation_term_term_id_index ON annotation_term USING btree (term_id);
CREATE INDEX IF NOT EXISTS annotation_term_user_id_index ON annotation_term USING btree (user_id);
CREATE INDEX IF NOT EXISTS algo_annotation_term_annotation_ident_index ON algo_annotation_term USING btree (annotation_ident);
CREATE INDEX IF NOT EXISTS algo_annotation_term_project_id_index ON algo_annotation_term USING btree (project_id);
CREATE INDEX IF NOT EXISTS algo_annotation_term_rate_index ON algo_annotation_term USING btree (rate);
CREATE INDEX IF NOT EXISTS algo_annotation_term_term_id_index ON algo_annotation_term USING btree (term_id);
CREATE INDEX IF NOT EXISTS algo_annotation_term_user_job_id_index ON algo_annotation_term USING btree (user_job_id);
CREATE INDEX IF NOT EXISTS relation_term_relation_id_index ON relation_term USING btree (relation_id);
CREATE INDEX IF NOT EXISTS relation_term_term1_id_index ON relation_term USING btree (term1_id);
CREATE INDEX IF NOT EXISTS relation_term_term2_id_index ON relation_term USING btree (term2_id);
CREATE INDEX IF NOT EXISTS term_ontology_id_index ON term USING btree (ontology_id);
CREATE INDEX IF NOT EXISTS track_image_id_index ON track USING btree (image_id);
CREATE INDEX IF NOT EXISTS track_project_id_index ON track USING btree (project_id);
CREATE INDEX IF NOT EXISTS annotation_track_track_id_index ON annotation_track USING btree (track_id);
CREATE INDEX IF NOT EXISTS annotation_track_slice_id_index ON annotation_track USING btree (slice_id);
CREATE INDEX IF NOT EXISTS annotation_track_annotation_ident_index ON annotation_track USING btree (annotation_ident);
CREATE INDEX IF NOT EXISTS sec_user_job_id_index ON sec_user USING btree (job_id);
CREATE INDEX IF NOT EXISTS sec_user_user_id_index ON sec_user USING btree (user_id);
CREATE INDEX IF NOT EXISTS job_project_id_index ON job USING btree (project_id);
CREATE INDEX IF NOT EXISTS job_software_id_index ON job USING btree (software_id);
CREATE INDEX IF NOT EXISTS job_processing_server_id_index ON job USING btree (processing_server_id);
CREATE INDEX IF NOT EXISTS job_parameter_job_id_index ON job_parameter USING btree (job_id);
CREATE INDEX IF NOT EXISTS job_parameter_software_parameter_id_index ON job_parameter USING btree (software_parameter_id);
CREATE INDEX IF NOT EXISTS software_parameter_software_id_index ON software_parameter USING btree (software_id);
CREATE INDEX IF NOT EXISTS software_project_software_id_index ON software_project USING btree (software_id);
CREATE INDEX IF NOT EXISTS software_project_project_id_index ON software_project USING btree (project_id);
CREATE INDEX IF NOT EXISTS user_group_user_id_index ON user_group USING btree (user_id);
CREATE INDEX IF NOT EXISTS user_group_group_id_index ON user_group USING btree (group_id);
CREATE INDEX IF NOT EXISTS auth_with_token_user_id_index ON auth_with_token USING btree (user_id);
CREATE INDEX IF NOT EXISTS auth_with_token_token_key_index ON auth_with_token USING hash (token_key);