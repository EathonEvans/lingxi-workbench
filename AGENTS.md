# Lingxi Workbench Server

## 项目模块

- `framework`：共享工程能力，不依赖业务模块。
- `infra-ai`：AI 端口与供应商适配，只依赖 `framework`。
- `admin`：独立管理端服务，承载管理员认证和管理能力，只依赖 `framework`。
- `application`：独立用户端服务和工作台业务，依赖 `framework`、`infra-ai`。
- `mcp-server`：独立 MCP 协议服务，不依赖 `admin` 或 `application`。

## 架构原则

1. `admin` 与 `application` 的业务代码统一使用传统三层架构，默认不使用 DDD、六边形架构或领域端口抽象。
2. 代码按照“业务模块优先，模块内按职责分层”组织。
3. 业务调用方向固定为 `Controller -> Service -> Repo -> Mapper -> Database`。
4. Controller 不直接调用 Repo 或 Mapper；Service 不直接调用 Mapper；Repo 不依赖 Service 或 Controller。
5. 同一启动模块内的跨业务调用只能经由对方 Service，不得直接访问对方 Repo 或 Mapper。
6. `admin`、`application` 等独立服务之间通过稳定的 HTTP/Internal API 交互，不得直接依赖对方业务实现。
7. 不得新增 `api/application/domain/infrastructure` 式业务目录。

## 业务目录规范

以 `auth` 为例：

```text
auth
├── controller
├── service
│   └── impl
├── repo
│   └── mapper
├── model
│   ├── request
│   ├── vo
│   ├── entity
│   ├── bo
│   └── converter
├── common
│   ├── enums
│   └── constants
├── config
├── interceptor
└── context
```

只在存在对应代码时创建目录，禁止提前建立空包。

### 分层职责

- `controller`：负责请求参数校验、调用 Service 和返回统一响应体，不承载业务流程、事务或数据库查询。
- `service`：定义业务能力接口，使用 `XxxService` 命名。
- `service/impl`：负责业务流程、事务控制和业务校验，使用 `XxxServiceImpl` 命名。
- `repo`：封装数据查询、持久化语义和多 Mapper 组合，不承载业务规则。默认使用具体类，不创建无实际用途的 Repo 接口和 `impl`。
- `repo/mapper`：负责 MyBatis/MyBatis-Plus SQL、CRUD 和数据库结果映射，不处理业务逻辑。
- `model/request`：Controller 请求参数，使用 `XxxRequest` 命名。
- `model/vo`：对外接口响应数据，统一使用 `XxxVO` 命名，不再并存 `Response`、`Resp`、`Result` 等同义模型。
- `model/entity`：数据库表映射对象，使用 `XxxEntity` 命名，不得直接作为接口响应返回。
- `model/bo`：Service 和 Repo 间的业务对象，使用 `XxxBO` 命名。只有出现组合数据或业务计算时才创建。
- `model/converter`：集中存放 MapStruct 转换器，负责 Entity、BO、VO 间的转换，使用 `XxxConverter` 命名。
- `config`、`interceptor`、`context`：分别存放当前业务的配置、Web 拦截器和请求上下文。

## 模型精简规范

1. 禁止为简单接口机械创建 `Request -> DTO -> Command -> BO -> Result -> Response -> VO` 完整对象链。
2. 简单请求可直接传入 Service；只在需要独立业务语义时才增加 BO。
3. 已存在 MapStruct Converter 时，禁止再编写重复的手工转换逻辑。
4. 简单构造且没有复用价值时，不强制增加 Converter。

## Common 作用域

- 只被单个业务使用的枚举和常量放在 `<business>/common/enums` 和 `<business>/common/constants`。
- 被当前 Maven 模块多个业务共用时，放在与业务目录同级的 `common/enums` 和 `common/constants`。
- 被 `admin`、`application` 等多个 Maven 模块共用时，放入 `framework` 对应目录。
- 不因“未来可能复用”提前提升作用域，达到实际共享条件后再迁移。
- `common` 不是杂物目录，工具类、配置类和异常类必须放入职责明确的包。
- 常量包名统一使用 `constants`。

## 枚举规范

1. 每个枚举必须定义 `code` 和 `describe` 两个字段，分别表达稳定编码和中文说明；禁止只声明枚举常量。
2. `code` 是程序交互、持久化和外部契约使用的稳定值，不得依赖 `ordinal()`，也不得使用 `name()` 代替业务编码。
3. 映射数据库普通单字段的枚举，必须在 `code` 字段上标注 MyBatis-Plus `@EnumValue`，实体字段直接使用枚举类型，禁止手写 `name()`、`valueOf()` 完成持久化转换。
4. JSON/JSONB 中的枚举由项目统一的 Fastjson2 TypeHandler 负责序列化，不得误用 `@EnumValue` 控制 JSON 内容。
5. `describe` 只用于中文说明和界面展示，不参与业务判断；业务判断统一比较枚举常量或 `code`。
6. 新增枚举值只维护 Java 枚举。数据库枚举字段使用字符类型，不建立枚举白名单 `CHECK (... IN (...))` 约束。

示例：

```java
@Getter
@RequiredArgsConstructor
public enum EnableStatus {
    ACTIVE("ACTIVE", "启用"),
    DISABLED("DISABLED", "禁用");

    @EnumValue
    private final String code;
    private final String describe;
}
```

## 框架与工具使用规范

1. 实现通用能力前，必须先检查 JDK、Spring、MyBatis-Plus、Hutool、Fastjson2、MapStruct、Lombok 以及项目 `framework` 是否已有语义一致的实现。
2. 选择顺序为：JDK 标准能力、项目现有框架能力、项目已引入的成熟工具类、最后才是自定义实现；不得为了一个简单方法新增工具依赖。
3. 字符串判空与处理、集合判空与转换、对象判空、Bean 转换、JSON 序列化、分页、条件构造、枚举映射、缓存操作等通用逻辑，禁止重复手写。
4. 已有工具方法与业务语义完全一致时直接复用，例如 `CharSequenceUtil.isBlank`、MyBatis-Plus `Wrappers`、MapStruct Converter、`@EnumValue` 和项目统一的 JSON TypeHandler。
5. 工具方法不能准确表达业务规则时，应封装语义清晰的业务方法，禁止为了“使用工具类”拼接晦涩调用或改变原有业务语义。
6. 优化现有代码时优先删除重复实现和无效封装，不新增仅转调一次、没有统一策略价值的工具类。

## 其他不变式

1. Workflow Version、Run Snapshot、Step Attempt、Run Event 不可覆盖。
2. 所有资源访问必须校验 Project Scope。
3. Redis 不是 Run 事实来源。
4. 密钥、Prompt 正文和 Tool 参数默认不进入日志。
5. 已进入生产的数据库变更脚本禁止修改，必须新增脚本演进。
6. 测试包结构应镜像对应的生产代码包结构。
7. 后续新增或修改的代码注释统一使用中文。
8. 管理端业务接口使用动作可读的路径命名，例如 `/list`、`/create`、`/update/{id}`、`/delete/{id}`；避免仅依赖 HTTP Method 区分同一路径语义，确保通过访问日志中的 URL 即可识别操作。
9. Java 代码禁止使用 `var` 声明局部变量，必须显式写出变量类型，生产代码与测试代码均遵循该规则。
10. `createdAt`、`updatedAt` 统一继承 `AuditTimeEntity`，由 framework 的 MyBatis-Plus 填充器维护；业务代码不得手动赋值。`deletedAt`、`completedAt`、`lastMessageAt` 等具有业务语义的时间字段仍由业务流程显式维护。

## 常用命令

```bash
./scripts/bootstrap.sh
./scripts/dev-start.sh
./scripts/verify.sh
./scripts/dev-stop.sh
```

## 完成标准

- 编译、单元测试和架构测试通过。
- API 变更包含相应测试并检查兼容性。
- 数据变更通过新脚本向前演进。
- 新运行路径包含 traceId、runId 和可操作错误码。
