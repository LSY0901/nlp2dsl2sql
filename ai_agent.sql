/**
  agent
│
├── dsl_entity              （embedding）                 实体定义:业务实体对应的数据库表以及存在的意义
│
├── dsl_attribute           （embedding）                 属性定义:实体对应数据库表中具体栏位的意义
│
├── dsl_relation                                         实体关系:实体中之间的关系 通过什么关联
│
├── dsl_metric              （embedding）                 指标定义:维护业务指标与实体之间的规则关系
│
├── dsl_metric_attribute                                 指标字段依赖:指标与实体属性之间的关系
│
├── dsl_dimension           （embedding）                 查询维度:维度与实体之间的关系
│
├── dsl_dimension_value     （embedding）                 维度值:维度值与数据库真实值的关系
│
├── dsl_metric_dimension                                 指标维度约束:指标与维度之间的关系
│
├── dsl_filter                                           业务过滤规则:业务规则与实体之间的过滤条件
│
└── dsl_synonym             （embedding）                 业务同义词:业务的不通表达方式对应的指标（这里后续可以用来做QueryRewrite）

 */

/*==============================================================*/
/* table: dsl_entity                                             */
/*==============================================================*/
drop table if exists agent.dsl_entity;
drop sequence if exists agent.dsl_entity_id_seq;
create sequence agent.dsl_entity_id_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue cache 1;
alter sequence agent.dsl_entity_id_seq owner to agent_user;
create table agent.dsl_entity
(
    id             int4 not null default nextval('agent.dsl_entity_id_seq'::regclass),
    entity_code    varchar(64),
    entity_name    varchar(128),
    entity_type    varchar(32),
    physical_table varchar(128),
    primary_key    varchar(64),
    description    varchar(500),
    embedding      vector(1024),
    is_deleted     bool          default false,
    creator        varchar(64),
    created_dt     int8,
    last_editor    varchar(64),
    last_edited_dt int8,
    constraint dsl_entity_pkey primary key (id)
);

alter table agent.dsl_entity owner to agent_user;

create unique index uk_dsl_entity_code on agent.dsl_entity (entity_code);

comment
on table agent.dsl_entity is 'DSL业务实体定义表，用于描述自然语言中的业务对象，例如学生、成绩、班级';
comment
on column agent.dsl_entity.id is '主键ID';
comment
on column agent.dsl_entity.entity_code is '实体编码，例如student、score';
comment
on column agent.dsl_entity.entity_name is '实体名称，例如学生、成绩';
comment
on column agent.dsl_entity.entity_type is '实体类型，例如ENTITY';
comment
on column agent.dsl_entity.physical_table is '对应数据库物理表名称';
comment
on column agent.dsl_entity.primary_key is '物理表主键字段';
comment
on column agent.dsl_entity.description is '实体业务描述，用于LLM理解实体含义';
comment
on column agent.dsl_entity.embedding is 'BGE-M3生成的1024维语义向量，用于实体语义检索';
comment
on column agent.dsl_entity.is_deleted is '逻辑删除标识';
comment
on column agent.dsl_entity.creator is '创建人';
comment
on column agent.dsl_entity.created_dt is '创建时间戳';
comment
on column agent.dsl_entity.last_editor is '最后修改人';
comment
on column agent.dsl_entity.last_edited_dt is '最后修改时间戳';

/*==============================================================*/
/* table: dsl_attribute                                          */
/*==============================================================*/
drop table if exists agent.dsl_attribute;
drop sequence if exists agent.dsl_attribute_id_seq;
create sequence agent.dsl_attribute_id_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue cache 1;
alter sequence agent.dsl_attribute_id_seq owner to agent_user;
create table agent.dsl_attribute
(
    id              int4 not null default nextval('agent.dsl_attribute_id_seq'::regclass),
    entity_code     varchar(64),
    attribute_code  varchar(64),
    attribute_name  varchar(128),
    physical_column varchar(128),
    data_type       varchar(32),
    attribute_type  varchar(32),
    description     varchar(500),
    embedding       vector(1024),
    is_queryable    bool          default true,
    is_aggregatable bool          default false,
    is_deleted      bool          default false,
    creator         varchar(64),
    created_dt      int8,
    last_editor     varchar(64),
    last_edited_dt  int8,
    constraint dsl_attribute_pkey primary key (id)
);

alter table agent.dsl_attribute owner to agent_user;

create index ix_dsl_attribute_entity_code on agent.dsl_attribute (entity_code);
create index ix_dsl_attribute_code on agent.dsl_attribute (attribute_code);
create unique index uk_dsl_attribute on agent.dsl_attribute (entity_code, attribute_code);

comment
on table agent.dsl_attribute is 'DSL业务属性定义表，用于描述实体字段语义以及SQL字段映射';
comment
on column agent.dsl_attribute.id is '主键ID';
comment
on column agent.dsl_attribute.entity_code is '所属实体编码，对应dsl_entity.entity_code';
comment
on column agent.dsl_attribute.attribute_code is '属性编码，例如student_name、score_value';
comment
on column agent.dsl_attribute.attribute_name is '属性名称，例如学生姓名、成绩';
comment
on column agent.dsl_attribute.physical_column is '对应物理表字段名称';
comment
on column agent.dsl_attribute.data_type is '字段数据类型，例如varchar、int、decimal';
comment
on column agent.dsl_attribute.attribute_type is '属性类型，例如ATTRIBUTE普通属性、MEASURE度量属性';
comment
on column agent.dsl_attribute.description is '属性业务描述，用于LLM理解字段含义';
comment
on column agent.dsl_attribute.embedding is 'BGE-M3生成的1024维语义向量，用于字段语义检索';
comment
on column agent.dsl_attribute.is_queryable is '是否允许作为查询字段或过滤条件';
comment
on column agent.dsl_attribute.is_aggregatable is '是否支持聚合计算，例如AVG、SUM、MAX';
comment
on column agent.dsl_attribute.is_deleted is '逻辑删除标识';
comment
on column agent.dsl_attribute.creator is '创建人';
comment
on column agent.dsl_attribute.created_dt is '创建时间戳';
comment
on column agent.dsl_attribute.last_editor is '最后修改人';
comment
on column agent.dsl_attribute.last_edited_dt is '最后修改时间戳';

/*==============================================================*/
/* table: dsl_relation                                           */
/*==============================================================*/
drop table if exists agent.dsl_relation;
drop sequence if exists agent.dsl_relation_id_seq;
create sequence agent.dsl_relation_id_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue cache 1;
alter sequence agent.dsl_relation_id_seq owner to agent_user;
create table agent.dsl_relation
(
    id             int4 not null default nextval('agent.dsl_relation_id_seq'::regclass),
    relation_code  varchar(64),
    source_entity  varchar(64),
    target_entity  varchar(64),
    relation_type  varchar(32),
    join_type      varchar(32),
    join_condition varchar(500),
    priority       int4          default 0,
    description    varchar(500),
    is_deleted     bool          default false,
    creator        varchar(64),
    created_dt     int8,
    last_editor    varchar(64),
    last_edited_dt int8,
    constraint dsl_relation_pkey primary key (id)
);

alter table agent.dsl_relation owner to agent_user;

create index ix_dsl_relation_source_entity on agent.dsl_relation (source_entity);
create index ix_dsl_relation_target_entity on agent.dsl_relation (target_entity);
create unique index uk_dsl_relation_code on agent.dsl_relation (relation_code);

comment
on table agent.dsl_relation is 'DSL实体关系定义表，用于描述业务实体之间关联关系以及SQL JOIN规则';
comment
on column agent.dsl_relation.id is '主键ID';
comment
on column agent.dsl_relation.relation_code is '关系编码，唯一标识实体之间关联关系';
comment
on column agent.dsl_relation.source_entity is '源实体编码，对应dsl_entity.entity_code';
comment
on column agent.dsl_relation.target_entity is '目标实体编码，对应dsl_entity.entity_code';
comment
on column agent.dsl_relation.relation_type is '实体关系类型，例如ONE_TO_ONE、ONE_TO_MANY、MANY_TO_ONE';
comment
on column agent.dsl_relation.join_type is 'SQL连接类型，例如INNER JOIN、LEFT JOIN';
comment
on column agent.dsl_relation.join_condition is '实体关联条件，用于自动生成SQL JOIN语句';
comment
on column agent.dsl_relation.priority is '关系优先级，用于多条JOIN路径选择';
comment
on column agent.dsl_relation.description is '关系业务描述，用于LLM理解实体之间关系';
comment
on column agent.dsl_relation.is_deleted is '逻辑删除标识';
comment
on column agent.dsl_relation.creator is '创建人';
comment
on column agent.dsl_relation.created_dt is '创建时间戳';
comment
on column agent.dsl_relation.last_editor is '最后修改人';
comment
on column agent.dsl_relation.last_edited_dt is '最后修改时间戳';

/*==============================================================*/
/* table: dsl_metric                                            */
/*==============================================================*/
drop table if exists agent.dsl_metric;
drop sequence if exists agent.dsl_metric_id_seq;
create sequence agent.dsl_metric_id_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue cache 1;
alter sequence agent.dsl_metric_id_seq owner to agent_user;
create table agent.dsl_metric
(
    id               int4 not null default nextval('agent.dsl_metric_id_seq'::regclass),
    metric_code      varchar(64),
    metric_name      varchar(128),
    metric_type      varchar(32),
    entity_code      varchar(64),
    aggregation_type varchar(32),
    expression       varchar(1000),
    unit             varchar(32),
    precision_value  int4,
    result_type      varchar(32),
    description      varchar(500),
    embedding        vector(1024),
    is_deleted       bool          default false,
    creator          varchar(64),
    created_dt       int8,
    last_editor      varchar(64),
    last_edited_dt   int8,
    constraint dsl_metric_pkey primary key (id)
);

alter table agent.dsl_metric owner to agent_user;

create unique index uk_dsl_metric_code on agent.dsl_metric (metric_code);
create index ix_dsl_metric_entity_code on agent.dsl_metric (entity_code);

comment
on table agent.dsl_metric is 'DSL业务指标定义表，用于描述自然语言中的业务指标、计算逻辑以及语义向量';
comment
on column agent.dsl_metric.id is '主键ID';
comment
on column agent.dsl_metric.metric_code is '指标编码，例如avg_score、student_count';
comment
on column agent.dsl_metric.metric_name is '指标名称，例如平均成绩、不及格率';
comment
on column agent.dsl_metric.metric_type is '指标业务类型，例如QUALITY、STATISTICS';
comment
on column agent.dsl_metric.entity_code is '指标所属业务实体编码，对应dsl_entity.entity_code';
comment
on column agent.dsl_metric.aggregation_type is '聚合类型，例如AVG、SUM、COUNT、MAX、MIN、RATE';
comment
on column agent.dsl_metric.expression is '指标计算表达式，用于生成SQL，例如AVG(score.score_value)';
comment
on column agent.dsl_metric.unit is '指标单位，例如分、%、人数';
comment
on column agent.dsl_metric.precision_value is '指标结果保留小数位数';
comment
on column agent.dsl_metric.result_type is '指标结果类型，例如NUMBER、PERCENT、INTEGER';
comment
on column agent.dsl_metric.description is '指标业务描述，用于LLM理解指标口径';
comment
on column agent.dsl_metric.embedding is 'BGE-M3生成的1024维语义向量，用于指标语义检索';
comment
on column agent.dsl_metric.is_deleted is '逻辑删除标识';
comment
on column agent.dsl_metric.creator is '创建人';
comment
on column agent.dsl_metric.created_dt is '创建时间戳';
comment
on column agent.dsl_metric.last_editor is '最后修改人';
comment
on column agent.dsl_metric.last_edited_dt is '最后修改时间戳';

/*==============================================================*/
/* table: dsl_metric_attribute                                   */
/*==============================================================*/
drop table if exists agent.dsl_metric_attribute;
drop sequence if exists agent.dsl_metric_attribute_id_seq;
create sequence agent.dsl_metric_attribute_id_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue cache 1;
alter sequence agent.dsl_metric_attribute_id_seq owner to agent_user;
create table agent.dsl_metric_attribute
(
    id             int4 not null default nextval('agent.dsl_metric_attribute_id_seq'::regclass),
    metric_code    varchar(64),
    entity_code    varchar(64),
    attribute_code varchar(64),
    role_type      varchar(32),
    description    varchar(500),
    is_deleted     bool          default false,
    creator        varchar(64),
    created_dt     int8,
    last_editor    varchar(64),
    last_edited_dt int8,
    constraint dsl_metric_attribute_pkey primary key (id)
);

alter table agent.dsl_metric_attribute owner to agent_user;

create index ix_dsl_metric_attribute_metric_code on agent.dsl_metric_attribute (metric_code);
create index ix_dsl_metric_attribute_attribute_code on agent.dsl_metric_attribute (attribute_code);
create unique index uk_dsl_metric_attribute on agent.dsl_metric_attribute (metric_code, attribute_code);

comment
on table agent.dsl_metric_attribute is 'DSL指标属性依赖关系表，用于描述指标计算依赖的业务字段';
comment
on column agent.dsl_metric_attribute.id is '主键ID';
comment
on column agent.dsl_metric_attribute.metric_code is '指标编码，对应dsl_metric.metric_code';
comment
on column agent.dsl_metric_attribute.entity_code is '属性所属实体编码，对应dsl_entity.entity_code';
comment
on column agent.dsl_metric_attribute.attribute_code is '指标依赖属性编码，对应dsl_attribute.attribute_code';
comment
on column agent.dsl_metric_attribute.role_type is '字段角色类型，例如MEASURE计算字段、FILTER过滤字段、GROUP维度字段';
comment
on column agent.dsl_metric_attribute.description is '指标字段依赖说明';
comment
on column agent.dsl_metric_attribute.is_deleted is '逻辑删除标识';
comment
on column agent.dsl_metric_attribute.creator is '创建人';
comment
on column agent.dsl_metric_attribute.created_dt is '创建时间戳';
comment
on column agent.dsl_metric_attribute.last_editor is '最后修改人';
comment
on column agent.dsl_metric_attribute.last_edited_dt is '最后修改时间戳';

/*==============================================================*/
/* table: dsl_dimension                                          */
/*==============================================================*/
drop table if exists agent.dsl_dimension;
drop sequence if exists agent.dsl_dimension_id_seq;
create sequence agent.dsl_dimension_id_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue cache 1;
alter sequence agent.dsl_dimension_id_seq owner to agent_user;
create table agent.dsl_dimension
(
    id              int4 not null default nextval('agent.dsl_dimension_id_seq'::regclass),
    dimension_code  varchar(64),
    dimension_name  varchar(128),
    entity_code     varchar(64),
    attribute_code  varchar(64),
    dimension_type  varchar(32),
    physical_column varchar(128),
    description     varchar(500),
    embedding       vector(1024),
    is_queryable    bool          default true,
    is_deleted      bool          default false,
    creator         varchar(64),
    created_dt      int8,
    last_editor     varchar(64),
    last_edited_dt  int8,
    constraint dsl_dimension_pkey primary key (id)
);

alter table agent.dsl_dimension owner to agent_user;

create unique index uk_dsl_dimension_code on agent.dsl_dimension (dimension_code);
create index ix_dsl_dimension_entity_code on agent.dsl_dimension (entity_code);

comment
on table agent.dsl_dimension is 'DSL查询维度定义表，用于描述业务分析中的分组维度，例如年级、班级、科目';
comment
on column agent.dsl_dimension.id is '主键ID';
comment
on column agent.dsl_dimension.dimension_code is '维度编码，例如grade、class、subject';
comment
on column agent.dsl_dimension.dimension_name is '维度名称，例如年级、班级、科目';
comment
on column agent.dsl_dimension.entity_code is '所属实体编码，对应dsl_entity.entity_code';
comment
on column agent.dsl_dimension.attribute_code is '对应属性编码，对应dsl_attribute.attribute_code';
comment
on column agent.dsl_dimension.dimension_type is '维度类型，例如ATTRIBUTE、ENUM';
comment
on column agent.dsl_dimension.physical_column is '对应数据库物理字段';
comment
on column agent.dsl_dimension.description is '维度业务描述，用于LLM理解维度含义';
comment
on column agent.dsl_dimension.embedding is 'BGE-M3生成的1024维语义向量，用于维度语义检索';
comment
on column agent.dsl_dimension.is_queryable is '是否允许作为查询分组维度';
comment
on column agent.dsl_dimension.is_deleted is '逻辑删除标识';
comment
on column agent.dsl_dimension.creator is '创建人';
comment
on column agent.dsl_dimension.created_dt is '创建时间戳';
comment
on column agent.dsl_dimension.last_editor is '最后修改人';
comment
on column agent.dsl_dimension.last_edited_dt is '最后修改时间戳';

/*==============================================================*/
/* table: dsl_dimension_value                                    */
/*==============================================================*/
drop table if exists agent.dsl_dimension_value;
drop sequence if exists agent.dsl_dimension_value_id_seq;
create sequence agent.dsl_dimension_value_id_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue cache 1;
alter sequence agent.dsl_dimension_value_id_seq owner to agent_user;
create table agent.dsl_dimension_value
(
    id             int4 not null default nextval('agent.dsl_dimension_value_id_seq'::regclass),
    dimension_code varchar(64),
    value_code     varchar(64),
    value_name     varchar(128),
    physical_value varchar(128),
    description    varchar(500),
    embedding      vector(1024),
    is_deleted     bool          default false,
    creator        varchar(64),
    created_dt     int8,
    last_editor    varchar(64),
    last_edited_dt int8,
    constraint dsl_dimension_value_pkey primary key (id)
);

alter table agent.dsl_dimension_value owner to agent_user;

create index ix_dsl_dimension_value_dimension_code on agent.dsl_dimension_value (dimension_code);
create index ix_dsl_dimension_value_value_code on agent.dsl_dimension_value (value_code);

comment
on table agent.dsl_dimension_value is 'DSL维度值定义表，用于描述业务维度中的具体枚举值以及数据库映射';
comment
on column agent.dsl_dimension_value.id is '主键ID';
comment
on column agent.dsl_dimension_value.dimension_code is '维度编码，对应dsl_dimension.dimension_code';
comment
on column agent.dsl_dimension_value.value_code is '维度值编码，例如grade_1、math';
comment
on column agent.dsl_dimension_value.value_name is '维度值名称，例如一年级、数学';
comment
on column agent.dsl_dimension_value.physical_value is '数据库实际存储值';
comment
on column agent.dsl_dimension_value.description is '维度值业务描述';
comment
on column agent.dsl_dimension_value.embedding is 'BGE-M3生成的1024维语义向量，用于维度值语义检索';
comment
on column agent.dsl_dimension_value.is_deleted is '逻辑删除标识';
comment
on column agent.dsl_dimension_value.creator is '创建人';
comment
on column agent.dsl_dimension_value.created_dt is '创建时间戳';
comment
on column agent.dsl_dimension_value.last_editor is '最后修改人';
comment
on column agent.dsl_dimension_value.last_edited_dt is '最后修改时间戳';

/*==============================================================*/
/* table: dsl_metric_dimension                                   */
/*==============================================================*/
drop table if exists agent.dsl_metric_dimension;
drop sequence if exists agent.dsl_metric_dimension_id_seq;
create sequence agent.dsl_metric_dimension_id_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue cache 1;
alter sequence agent.dsl_metric_dimension_id_seq owner to agent_user;
create table agent.dsl_metric_dimension
(
    id             int4 not null default nextval('agent.dsl_metric_dimension_id_seq'::regclass),
    metric_code    varchar(64),
    dimension_code varchar(64),
    relation_type  varchar(32),
    description    varchar(500),
    is_required    bool          default false,
    is_deleted     bool          default false,
    creator        varchar(64),
    created_dt     int8,
    last_editor    varchar(64),
    last_edited_dt int8,
    constraint dsl_metric_dimension_pkey primary key (id)
);

alter table agent.dsl_metric_dimension owner to agent_user;

create index ix_dsl_metric_dimension_metric_code on agent.dsl_metric_dimension (metric_code);
create index ix_dsl_metric_dimension_dimension_code on agent.dsl_metric_dimension (dimension_code);
create unique index uk_dsl_metric_dimension on agent.dsl_metric_dimension (metric_code, dimension_code);

comment
on table agent.dsl_metric_dimension is 'DSL指标维度关系表，用于限制指标支持的分析维度，避免LLM生成非法组合';
comment
on column agent.dsl_metric_dimension.id is '主键ID';
comment
on column agent.dsl_metric_dimension.metric_code is '指标编码，对应dsl_metric.metric_code';
comment
on column agent.dsl_metric_dimension.dimension_code is '维度编码，对应dsl_dimension.dimension_code';
comment
on column agent.dsl_metric_dimension.relation_type is '指标维度关系类型，例如GROUP、FILTER';
comment
on column agent.dsl_metric_dimension.description is '指标支持该维度的业务说明';
comment
on column agent.dsl_metric_dimension.is_required is '是否必须指定该维度';
comment
on column agent.dsl_metric_dimension.is_deleted is '逻辑删除标识';
comment
on column agent.dsl_metric_dimension.creator is '创建人';
comment
on column agent.dsl_metric_dimension.created_dt is '创建时间戳';
comment
on column agent.dsl_metric_dimension.last_editor is '最后修改人';
comment
on column agent.dsl_metric_dimension.last_edited_dt is '最后修改时间戳';

/*==============================================================*/
/* table: dsl_filter                                             */
/*==============================================================*/
drop table if exists agent.dsl_filter;
drop sequence if exists agent.dsl_filter_id_seq;
create sequence agent.dsl_filter_id_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue cache 1;
alter sequence agent.dsl_filter_id_seq owner to agent_user;
create table agent.dsl_filter
(
    id             int4 not null default nextval('agent.dsl_filter_id_seq'::regclass),
    filter_code    varchar(64),
    filter_name    varchar(128),
    entity_code    varchar(64),
    attribute_code varchar(64),
    operator_type  varchar(32),
    expression     varchar(1000),
    description    varchar(500),
    is_system      bool          default false,
    is_deleted     bool          default false,
    creator        varchar(64),
    created_dt     int8,
    last_editor    varchar(64),
    last_edited_dt int8,
    constraint dsl_filter_pkey primary key (id)
);

alter table agent.dsl_filter owner to agent_user;

create unique index uk_dsl_filter_code on agent.dsl_filter (filter_code);
create index ix_dsl_filter_entity_code on agent.dsl_filter (entity_code);

comment
on table agent.dsl_filter is 'DSL业务过滤规则定义表，用于描述固定业务条件和SQL过滤逻辑';
comment
on column agent.dsl_filter.id is '主键ID';
comment
on column agent.dsl_filter.filter_code is '过滤规则编码，例如valid_student、valid_score';
comment
on column agent.dsl_filter.filter_name is '过滤规则名称，例如有效学生、有效成绩';
comment
on column agent.dsl_filter.entity_code is '过滤所属实体编码，对应dsl_entity.entity_code';
comment
on column agent.dsl_filter.attribute_code is '过滤字段编码，对应dsl_attribute.attribute_code';
comment
on column agent.dsl_filter.operator_type is '操作符类型，例如EQ、GT、LT、BETWEEN';
comment
on column agent.dsl_filter.expression is '过滤SQL表达式，例如score.score_value >= 60';
comment
on column agent.dsl_filter.description is '过滤规则业务描述';
comment
on column agent.dsl_filter.is_system is '是否系统默认过滤规则';
comment
on column agent.dsl_filter.is_deleted is '逻辑删除标识';
comment
on column agent.dsl_filter.creator is '创建人';
comment
on column agent.dsl_filter.created_dt is '创建时间戳';
comment
on column agent.dsl_filter.last_editor is '最后修改人';
comment
on column agent.dsl_filter.last_edited_dt is '最后修改时间戳';

/*==============================================================*/
/* table: dsl_synonym                                            */
/*==============================================================*/
drop table if exists agent.dsl_synonym;
drop sequence if exists agent.dsl_synonym_id_seq;
create sequence agent.dsl_synonym_id_seq
    start with 1
    increment by 1
    no minvalue
    no maxvalue cache 1;
alter sequence agent.dsl_synonym_id_seq owner to agent_user;
create table agent.dsl_synonym
(
    id             int4 not null default nextval('agent.dsl_synonym_id_seq'::regclass),
    synonym_text   varchar(128),
    object_type    varchar(32),
    object_code    varchar(64),
    standard_name  varchar(128),
    description    varchar(500),
    embedding      vector(1024),
    is_deleted     bool          default false,
    creator        varchar(64),
    created_dt     int8,
    last_editor    varchar(64),
    last_edited_dt int8,
    constraint dsl_synonym_pkey primary key (id)
);

alter table agent.dsl_synonym owner to agent_user;

create index ix_dsl_synonym_text on agent.dsl_synonym (synonym_text);
create index ix_dsl_synonym_object_code on agent.dsl_synonym (object_code);

comment
on table agent.dsl_synonym is 'DSL业务同义词定义表，用于将用户自然语言表达映射到标准DSL对象';
comment
on column agent.dsl_synonym.id is '主键ID';
comment
on column agent.dsl_synonym.synonym_text is '用户可能输入的自然语言表达，例如均分、平均分';
comment
on column agent.dsl_synonym.object_type is '映射对象类型，例如ENTITY、ATTRIBUTE、METRIC、DIMENSION';
comment
on column agent.dsl_synonym.object_code is '映射目标编码，对应DSL对象编码';
comment
on column agent.dsl_synonym.standard_name is '标准业务名称';
comment
on column agent.dsl_synonym.description is '同义词业务说明';
comment
on column agent.dsl_synonym.embedding is 'BGE-M3生成的1024维语义向量，用于用户表达语义检索';
comment
on column agent.dsl_synonym.is_deleted is '逻辑删除标识';
comment
on column agent.dsl_synonym.creator is '创建人';
comment
on column agent.dsl_synonym.created_dt is '创建时间戳';
comment
on column agent.dsl_synonym.last_editor is '最后修改人';
comment
on column agent.dsl_synonym.last_edited_dt is '最后修改时间戳';
