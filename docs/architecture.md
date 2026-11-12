# 架构

**版本：** 第 12 周

浏览器、Java 平台和 Python AI 服务分三个进程边界。第 2 周只接通 mock 闭环。平台暂时用本机 Java 8 和 Spring Boot 2.7，不依赖另外下载的 Java 21。

```text
浏览器  ->  Java :8080  ->  Python :8000
              |  H2 文件库
              |  模拟点位、设备、事件
              |  Outbox
              |  Agent 运行与工具调用
```

Java 持有评测时钟、业务 ID 和落库。Python 只收到调用里的 `run_id`、`clock_mode`、`request_timestamp` 和 `tick`，不生成业务时间。

## 第 2 周闭环

1. 固定场景 `w02-mock-incident` 写入一个点位、一台摄像机和三条事件。
2. 每条事件在同一事务写入 `event` 和 `outbox`。提交后，进程内队列把 outbox 标成 `PUBLISHED`。
3. Java 把调查任务交给 Python。`MockAdapter` 选择 `search_events`。
4. Java 在自己的库里执行这个工具，再把结果交回 Python 生成报告。
5. 报告里的 `source_ids` 必须来自这次工具结果。对不上就记成证据不足。

`run_id` 固定为 `run-w02-mock-incident-20261110`。同一场景重复启动不会另起一套 ID。H2 使用文件库，进程重启后事件、Agent 运行和工具调用仍可查询。

Mock 运行在接口和页面上显示 `MOCK_MODE`，不发送模型请求，这条链路不计入真实 Agent 指标。非 Mock 模式由 `OpenAICompatibleAdapter` 向配置的 OpenAI-compatible 地址发送请求；没有真实配置和实测记录时仍按未验收处理。

## 第 3 周媒体

北门摄像机按虚拟时钟逐 tick 记录。没有事件的 tick 是 1 fps，事件所在 tick 在边界切到 4 fps。最近两个 tick 留在环缓里，所以事件发生时，前一个 tick 已经在缓冲中。每个事件导出前 1 tick、事件 tick、后 1 tick，写成浏览器可播放的 WAV，画面是按 tick 对齐的 PNG。

音频是同一条 8 kHz PCM。后一个 tick 的第一个采样紧接前一个 tick 的最后一个采样，片段只复制这条流上的区间，不补静音。文件在 `data/local/media/`，库里只存相对路径。已有第 2 周数据库时，下次启动会补上这套媒体，不重写事件。

页面可以播放整段音频和事件片段，也可以上传本机视频或音频文件再播放。不连接真实摄像头，也不启动 RTSP 服务。

上传入口只接受常见的音频和视频容器。服务端同时检查 MIME、扩展名和文件头；HTML、SVG 或伪装成媒体的未知内容返回 400。平台响应统一加 `nosniff` 和同源内容安全策略，上传文件不能变成同源脚本页面。

## 第 4 周检测与跟踪

Python 用 ONNX Runtime 跑 `ai-service/app/synthetic_detect.onnx`。这是仓库里的亮度卷积，不是下载的检测权重。1080p 合成画面先缩到宽 640，再进这个图。亮度超过阈值的像素收成一个框。跟踪只和最近 5 帧里的框做重合匹配，窗口外的相似框会新开一条轨迹。推理抛错时记入 `inference_fault`，这一帧不补点。

Java 持有 `track` 和 `track_point` 的 ID。样例固定 8 帧，重复调用得到同一条向右移动的轨迹。性能入口是 `ai-service/bench_detect.py`，计时的是降采样之后的检测。

## 第 5 周属性与结构化

每条轨迹只把属性头打在选中的那一帧上。样例轨迹质量够门限时，选中最早的优选帧，输出衣着 JSON 和一张打过遮挡条的小图。人脸区域在脱敏结果里固定写成 `redacted`，不产生人脸特征。

两条回退不共用取帧规则：

1. 质量或置信门控失败时，仍用优选时刻，但画面改成该时刻的整帧。
2. 推理异常或跟踪窗口被拉开时，只在仍然有效的帧里取质量最高的一帧，不取时间上最近的一帧。

`structured_observation` 用 `run_id + case_key` 唯一。同一场景再次启动不会再插入这三条记录，属性头也不会被再调用一次。页面把优选帧、门控回退和跟踪中断分成不同标记。

## 第 6 周端云路由

路由策略版本是 `route-w06-1`。不确定性由人脸质量、人体质量和轨迹连续性三项合成，中间结果保留 4 位，快照落库 2 位。评测时钟用场景里的 `q_scripted` 作为 `q_input`，真实时钟用 `q_observed`。快照同时记下这两个数和生效阈值 `theta_u_eff`。

每台模拟设备有一条固定的历史质量窗口。窗口短于 3 条时，决策标记 `degraded`，只走本地适配器，不上送。窗口足够且不确定性高于阈值时，才调用脚本化的云适配器。云适配器不发网络请求，结果来自这条 case 的固定脚本。本地适配器只读结构化结果里的衣着标签和人体质量。

融合只有三种：两边都过线且标签相同是 `agree`，都过线但标签不同是 `conflict`，其余是 `dual_low`。结论字段只有一个值。未知业务先被反向规则拦住，结论是 `unknown_business`，云适配器不会被调用。

`decision_snapshot` 用 `run_id + case_key` 唯一。同一场景再次启动不会另写一套决策。把快照里的输入再送进同一策略，得到的路由、融合和结论与已落库的一致。

10 条反向断言放在 `eval/reverse/w06.json`，由 `ReverseRules` 执行。本周路由使用其中「未知业务不得走公网」。其余九条是后续档案、检索、插件和证据动作要调用的同一套规则，现在已经可以单独跑过。

## 第 7 周 Agent 主链路

Java 持有六个 P0 工具的执行：`search_events`、`get_event_detail`、`search_person`、`build_trajectory`、`find_companions`、`export_evidence`。第 7 周当时人员档案和同伴还没有数据。档案检索、门点轨迹和同行在后面的周次接上。不带档案的轨迹工具仍返回本次 run 的摄像机轨迹。证据导出只返回磁盘上存在的片段；文件不在就报缺失。

Python 按任务选择下一步工具，最多四轮。同一工具和同一参数不会重复执行。报告必须符合 `contracts/agent-report.schema.json` 的字段。`source_ids` 只能来自本轮工具结果。第一次引用非法时，Java 把非法原因交回模型再写一次。第二次仍非法，结论固定为「证据不足，无法结案」，失败类型是 `citation_invalid`。没有任何可用 ID 时，结论是「证据不足」。

模型名、地址和密钥仍由项目所有者放在环境变量 `TRACEWITNESS_MODEL_BASE_URL`、`TRACEWITNESS_MODEL_API_KEY`、`TRACEWITNESS_MODEL_NAME` 里，不进仓库。`TRACEWITNESS_MODE=MOCK` 时用 `MockAdapter`，页面继续显示 `MOCK_MODE`，这条结果不计入真实 Agent 指标。其他模式由 `OpenAICompatibleAdapter` 向 `{base}/v1/chat/completions` 发送请求，temperature 固定为 0，seed 用评测主种子。调用失败记成 `model_failed`，结论同样是无法结案，不编造工具结果。

门禁、梯控只写入 `requested_actions`。落库前经高风险规则改成待人工确认，`executed` 固定为 false。

10 条正向场景在 `eval/positive/w07.json`。

## 第 8 周人员档案

门点图是固定的五扇门：大堂、一单元门、二单元门、楼梯间、屋顶。边只记旅行秒数，最短路按无向边计算。屋顶没有边，最短路是不可达。大堂到二单元门的最短时间是 70 秒。

门禁事件写在 `access_event`，不进入摄像机事件表，所以原有三条北门事件和媒体片段不变。每次抓拍是一条 `profile_sample`。能合并时，`profile_event` 记 `bound`；被旅行时间或双库分差拦住时记 `rejected`，样本不进入档心。

档案分居民档和陌生人档。相似度是余弦，落库前保留 2 位。达到 0.80 才算命中。两个库的分数都过线且差距小于 0.05 时，工单是 `pending_human`，不自动合并。没有命中且人脸质量达到 0.50 才新建陌生人档；低于 0.50 不建档。档心按样本质量加权，再保留 2 位。

两条硬约束先于合并：没有通路是 `unreachable`，观测时间短于最短路是 `travel_conflict`。这两种工单状态都是 `forbidden`。自动合并、禁止合并、待审批、批准和驳回各写一条 `audit_log`。批准把被合并档案的已绑定样本并入留下的档案，并重算档心。驳回不改档案。

住址取已绑定门禁里出现最多的门。并列时保留原来的住址。同一门点达到 3 次，另开一条 `open` 的频次工单。

`search_person` 只在已有档案的姓名、住址和库别里查找，不因为检索去新建人员。对不上就仍是空列表。

5 条正向场景在 `eval/positive/w08.json`，5 条反向场景在 `eval/reverse/w08.json`。

## 第 9 周检索与非机动车

检索在本次 run 的在档人员上做内存扫描，不另建向量库。一次查询可以带多张图。人脸分取这些图里的最大余弦，人体分按各图的人体质量做加权平均，两边都有时再取平均。属性硬条件在算分之前剔除，软条件只在融合分出来之后加 0.05，最高到 1.00。每条命中和每次剔除都带一句解释，顺序固定为 `hard_filter,face_max,body_weighted,fuse,soft_weight`。硬过滤之后没有命中时，不新建人员。

非机动车门线用脚本帧，不下载检测权重。只有入口场景里、连续 3 帧都是非机动车并且方向是进楼，才生成告警。出楼、帧被打断、行人或非入口场景都不告警。告警关联的开门动作经过高风险规则，状态是待人工确认，`executed` 保持 false。确认或驳回告警只改告警状态，不执行开门。

5 条正向场景在 `eval/positive/w09.json`，5 条反向场景在 `eval/reverse/w09.json`。接口骨架见 [api.md](api.md)。

## 第 10 周轨迹、证据与难例

轨迹沿第 8 周的门点图走。没有边的门点，例如屋顶，直接剔除，原因是 `unreachable`。相邻保留点的观测时间短于最短路时剔除，原因是 `travel_conflict`。这条过滤会再过一次反向规则 `unreachable_track`，规则没留下的点不会进入地图。

抽稀规则是 `first_last_and_door_change`：保留每段的起点、终点，以及门点发生变化的点。同一扇门上的中间点记为 `thinned`，不画进折线。地图坐标是固定的：大堂 (40, 80)，一单元门 (40, 40)，二单元门 (80, 40)，楼梯间 (10, 50)。

同行只比较已经保留的到访。两人档案不同、门点相同，并且时间差不超过 60 秒，才记一条 `companion_link`。同一对人和同一扇门只保留最小间隔。

证据包的清单是四行文本：轨迹、保留点、同行、来源。SHA-256 对这四行的 UTF-8 字节计算。缺文件时状态是 `evidence_missing`，不写哈希。校验用库存清单重算；提交的文本和库存不一致则失败。

难例初始状态是 `open`。标注成 `true_negative` 这类标签后状态变为 `labeled`，并写审计。导出只包含已标注的难例。候选模型对比固定为 `local-small` 0.62 和 `cloud-script` 0.81，优胜是分数更高的一方，`trained` 固定为 false。

`build_trajectory` 带档案时返回这条门点轨迹。不带档案时仍返回摄像机轨迹。`find_companions` 返回本次 run 的同行。`export_evidence` 仍要求片段文件存在，成功时附上证据包哈希。

10 条正向场景在 `eval/positive/w10.json`。

## 第 11 周插件

插件先验签。签名失败时不核对包内哈希，也不加载。哈希、ABI 和资源按这个顺序继续检查。资源超过 64MB 的包留在候选槽。健康分达到 0.80 的版本升为在用，上一版留着回滚。故障注入和人工回滚都写记录。

5 条场景在 `eval/positive/w11.json`。页面和 `/api/overview` 能看到清单和回滚记录。

## 第 12 周交付

这一周没有新的业务能力。`run_eval.bat` 跑现有测试并写出 [eval-report.md](eval-report.md)。启动、公开边界和演示预演分别在 [deploy.md](deploy.md)、[public-boundary.md](public-boundary.md) 和 [demo-script.md](demo-script.md)。

## 还没做

端云路由里的云适配器继续只读脚本。演示视频没有录制。真实模型的任务完成率和工具选择准确率没有测。
