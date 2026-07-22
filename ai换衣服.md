根据Z:\2projects\HsiaoWear\ai换衣服.md

给app部署ai试衣服的功能，在主页今日里面，设置衣柜按钮，在今日穿搭推荐里面，点击按钮，则把右侧的推荐的三个衣服，发送给api，以及左侧的这个用户上传的图片，一起发给ai，右侧的三个是可选的，但是至少一个衣服，然后获取的返回图片放置替换原来放在左侧的图片，然后以前的图片存起来，下面新增一个恢复默认图片的按钮，点击则恢复哈；然后右侧的衣服推荐可以点击进行选择，在衣橱数据库里面选择，这个选择是弹出一个框即可；展示衣服的图片和衣服的名字即可；


保存你的API Key
离开此对话框后，您将无法取回密钥。
立刻复制或下载您的API密钥。注意妥善保管，任何获取到该密钥的人，都能以您的身份发起服务请求，并产生费用。如果丢失，你可以重置或者创建新的密钥
API Key
sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
API Host
ws-945n1ndux9blxaek.cn-beijing.maas.aliyuncs.com
OpenAI 兼容地址
https://ws-945n1ndux9blxaek.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
DashScope
https://ws-945n1ndux9blxaek.cn-beijing.maas.aliyuncs.com/api/v1
描述
ai试衣
所属业务空间
默认业务空间
权限
全部

AI试衣-基础版API参考
更新时间：2026-02-11 15:22:53
复制 MD 格式
产品详情
我的收藏
AI试衣-基础版模型支持使用服饰平拍图片以及人物正面全身照，生成逼真的试衣效果图。

重要
本文档仅适用于“中国内地（北京）”地域，且必须使用该地域的API Key。

快速入口： 在线体验 ｜ AI试衣模型总览 ｜ 计费与限流 ｜ 免费额度 ｜ 新手API调用入门指南

相关API：api AI试衣-Plus版｜api AI试衣-图片精修｜api AI试衣-图片分割

模型能力
多种服饰试穿

单件上装试穿：模型随机生成下装，或保留模特原有下装。

单件下装试穿：模型随机生成上装，或保留模特原有上装。

上下装组合试穿：完整替换全身套装。

连衣裙/连体衣试穿

精细化控制

人脸策略：可选择保留模特原有人脸，或生成一张全新的随机人脸。

指定分辨率：可指定输出图片的尺寸，或保持与原图一致。

模型概览
模型简介






模型名称

计费单价

限流（主账号与RAM子账号共用）

免费额度（查看）

任务下发接口RPS限制

同时处理中任务数量

aitryon

0.20元/张

10

5

400张

说明
模型选型建议：若使用场景对试衣结果图片清晰度、服饰纹理细节和logo还原效果等方面有更高要求，且可接受一定时间的等待，推荐使用aitryon-plus模型。

计费与限流：aitryon 与aitryon-plus 模型的计费标准和调用频率限制不同，详情请参见计量计费。

模型效果示意



输入模特的全身正面照

输入服装平铺图

生成的试衣效果图

test_client_tryon

上装平铺图

image.jpeg

test_client_tryon

下装平铺图

image.jpeg

输入图片要求
高质量的输入是高质量输出的保障。在调用API前，请务必确保您的图片符合以下规范。

模特图要求


要求类别

详细说明

图片要求

文件大小：5KB ～ 5MB之间

分辨率：图片宽度和高度均需在 150px ～ 4096px 范围内

图片格式：支持JPG、JPEG、PNG、BMP、HEIC

链接要求：上传图片必须为公网可访问的HTTP/HTTPS地址，不支持本地路径

模特人物要求

人群要求：支持不同性别、肤色、年龄（6岁以上）的人物图

姿势要求：人物全身正面照，光照良好。人物手部展示完整，避免手臂交叉遮挡等情况

人物要求：保持图片中有且仅有一个完整的人

正确的人物图示例
image.png

image.png

image.png

image.png

错误的人物图示例






❌多人照片

❌非正面全身照

（避免上传侧身、坐姿、躺姿、半身照片）

❌人物服装遮挡

（避免手持物、包等）

❌光线过暗/模糊不清

image.png

image

image.png

image.png

服饰图要求


要求类别

详细说明

图片要求

文件大小：5KB ～ 5MB之间

分辨率：图片宽度和高度均需在 150px ～ 4096px 范围内

图片格式：支持JPG、JPEG、PNG、BMP、HEIC

链接要求：上传图片必须为公网可访问的 HTTP/HTTPS 地址，不支持本地路径

服饰要求

服饰类型：支持单件上装、下装、连衣裙；支持套装、上下装组合

服装类目：支持常见服饰品类。不支持内衣、婚纱礼服、特色民族服饰等

服饰要求：

单件服饰：服饰平铺拍摄，仅含单件服装

服饰无折叠/遮挡：衣服应舒展、平整，无褶皱或折叠遮挡

背景简约干净：图片背景简洁干净、色彩统一，保持服饰主体清晰，无复杂的光照阴影

服饰占比大：服饰的画面占比尽可能大，四周不宜留白过多，过多的背景留白会降低试衣效果

正确的服饰图示例












上装

image.jpeg

image.jpeg

image.jpeg

下装

image.jpeg

image.jpeg

image.webp

连衣裙/连体服

image.webp

image.webp

连衣裙_2

错误的服饰图示例




❌多件服装

❌非正面照

❌折叠遮挡

❌服装褶皱

image.jpeg

image.png

image.png

image.png

前提条件
AI试衣Plus API仅支持通过HTTP进行调用。

在调用前，您需要获取API Key，再配置API Key到环境变量。

HTTP调用
API提供一个异步接口，获取结果分为两步：

创建任务：创建图片生成任务，获取一个唯一的 task_id。

查询结果：使用 task_id 轮询任务状态，直到任务完成并获取结果。

步骤1：创建任务
发送 POST 请求创建试衣任务。

 
POST https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis
说明
因该模型调用耗时较长，故采用异步调用的方式创建任务。

任务创建后，系统会立即返回一个 task_id。在下一步中，需要使用此 task_id 在24小时内查询任务结果。

入参描述






字段

类型

传参方式

必选

描述

示例值

Content-Type

String

Header

是

请求类型：application/json。

application/json

Authorization

String

Header

是

API-Key，例如：Bearer sk-xxxx。

Bearer sk-xxxx

X-DashScope-Async

String

Header

是

固定值为 enable，表示使用异步调用方式。

enable

model

String

Body

是

指明需要调用的模型。

aitryon

input.person_image_url

String

Body

是

模特人物图片的公网URL。您也可在此获取临时公网URL。

5KB≤图像文件≤5M

150≤图像边长≤4096

格式支持：jpg、png、jpeg、bmp、heic

需保持图片中有且仅有一个完整的人

仅支持HTTP/HTTPS链接，不支持本地路径

模特图示例请参见模特图要求。

说明
请点击此处下载我们提供的模特图。

http://aaa/3.jpg

input.top_garment_url

String

Body

否

上装/连衣裙服饰图的公网URL。您也可在此获取临时公网URL。

5KB≤图像文件≤5M

150≤图像边长≤4096

格式支持：jpg、png、jpeg、bmp、heic

需上传服饰平拍图，保持服饰是单一主体且完整，背景干净，四周不宜留白过多

仅支持HTTP/HTTPS链接，不支持本地路径

服饰图示例请参见服饰图要求。

说明
top_garment_url 和 bottom_garment_url 至少提供一个。

如果不传此字段，模型将随机生成上装。

对于连衣裙/连体衣，请将图片URL填入此字段，并将 bottom_garment_url 留空。

http://aaa/1.jpg

input.bottom_garment_url

String

Body

否

下装服饰图的公网URL。您也可在此获取临时公网URL。

5KB≤图像文件≤5M

150≤图像边长≤4096

格式支持：jpg、png、jpeg、bmp、heic

需上传服饰平拍图，保持服饰是单一主体且完整，背景干净，四周不宜留白过多

仅支持HTTP/HTTPS链接，不支持本地路径

服饰图示例请参见服饰图要求。

说明
top_garment_url 和 bottom_garment_url 至少提供一个。

如果不传此字段，模型将随机生成下装。

http://aaa/2.jpg

parameters.resolution

Int

Body

否

输出图片的分辨率。

-1：默认值，与原图尺寸保持一致。

1024：表示 576x1024 分辨率。

1280：表示 720x1280 分辨率。

说明
若后续还需调用AI试衣-图片精修API，此值必须设为 -1。

-1

parameters.restore_face

Bool

Body

否

是否还原模特图中的人脸。

true：默认值，保留原图人脸。

false：随机生成一张新的人脸。

说明
若后续还需调用AI试衣-图片精修API，此值必须设为true。

true

出参描述




字段

类型

描述

示例值

output.task_id

String

异步任务的唯一ID。

a8532587-fa8c-4ef8-82be-0c46b17950d1

output.task_status

String

任务提交后的状态。

PENDING

request_id

String

本次请求的唯一ID。

7574ee8f-38a3-4b1e-9280-11c33ab46e51

请求示例
试穿上装试穿下装试穿上下装试穿连衣裙/连体服
试穿上装：传入top_garment_url（待试穿的上装），模型将随机生成下装。

 
curl --location 'https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis/' \
--header 'X-DashScope-Async: enable' \
--header "Authorization: Bearer $DASHSCOPE_API_KEY" \
--header 'Content-Type: application/json' \
--data '{
    "model": "aitryon",
    "input": {
        "person_image_url": "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20250626/ubznva/model_person.png",
        "top_garment_url": "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20250626/epousa/short_sleeve.jpeg"    
    },
    "parameters": {
        "resolution": -1,
        "restore_face": true
    }
 }'
保留模特原下装：包含两个步骤，如下：

调用AI试衣-图片分割API，获取模特下装图像URL。

调用本文的试衣 API，传入 top_garment_url（待试穿的上装）和 bottom_garment_url（分割获取的下装URL）。

 
curl --location 'https://dashscope.aliyuncs.com/api/v1/services/aigc/image2image/image-synthesis/' \
--header 'X-DashScope-Async: enable' \
--header "Authorization: Bearer $DASHSCOPE_API_KEY" \
--header 'Content-Type: application/json' \
--data '{
    "model": "aitryon",
    "input": {
        "person_image_url": "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20250626/ubznva/model_person.png",
        "top_garment_url": "https://help-static-aliyun-doc.aliyuncs.com/file-manage-files/zh-CN/20250626/epousa/short_sleeve.jpeg",
        "bottom_garment_url": "图片分割API输出的图像URL"    
    },
    "parameters": {
        "resolution": -1,
        "restore_face": true
    }
 }'
响应示例
成功响应异常响应
请保存 task_id，用于查询任务状态与结果。

 
{
    "output": {
        "task_status": "PENDING",
        "task_id": "0385dc79-5ff8-4d82-bcb6-xxxxxx"
    },
    "request_id": "4909100c-7b5a-9f92-bfe5-xxxxxx"
}
步骤2：根据任务ID查询结果
使用上一步获取的 task_id，发送 GET 请求查询任务状态和结果。请将 URL 中的{task_id} 替换为您的实际任务ID。

 
GET https://dashscope.aliyuncs.com/api/v1/tasks/{task_id}
说明
AI 试衣任务耗时较长（15～30秒不等），建议采用轮询机制，并设置合理的查询间隔（如 3-5 秒）来获取结果。

任务成功后返回的 image_url有效期为24小时，请及时下载并保存图片。

此查询接口的默认QPS为20。如需更高频次的查询或事件通知，请配置异步任务回调。

如需批量查询或取消任务，请参见管理异步任务。

入参描述






字段

类型

传参方式

必选

描述

示例值

Authorization

String

Header

是

API-Key，例如：Bearer sk-xxx。

Bearer sk-xxx

task_id

String

Url Path

是

需要查询任务的ID。

a8532587-fa8c-4ef8-82be-0c46b17950d1

出参描述




字段

类型

描述

示例值

output.task_id

String

查询的任务ID。

a8532587-fa8c-4ef8-82be-0c46b17950d1

output.task_status

String

任务状态。可能的值包括：

PENDING 排队中

PRE-PROCESSING 前置处理中

RUNNING 处理中

POST-PROCESSING 后置处理中

SUCCEEDED 成功

FAILED 失败

UNKNOWN 作业不存在或状态未知

CANCELED：任务取消成功

SUCCEEDED

output.image_url

String

生成的试衣效果图地址。

image_url有效期为24小时，请及时下载。

https://.../result.jpg?Expires=xxx

output.submit_time

String

任务提交时间。

2024-07-30 15:39:39.918

output.scheduled_time

String

任务执行时间。

2024-07-30 15:39:39.941

output.end_time

String

任务完成时间。

2024-07-30 15:39:55.080

output.code

String

错误码。任务失败时返回此参数。

InvalidParameter

output.message

String

错误详情。任务失败时返回此参数。

The request is missing required parameters or in a wrong format

usage.image_count

Int

本次请求生成的图片张数。

1

request_id

String

本次请求的唯一ID。

7574ee8f-38a3-4b1e-9280-11c33ab46e51

请求示例
将86ecf553-d340-4e21-xxxxxxxxx替换为真实的task_id。

 
curl -X GET https://dashscope.aliyuncs.com/api/v1/tasks/86ecf553-d340-4e21-xxxxxxxxx \
--header "Authorization: Bearer $DASHSCOPE_API_KEY"
说明
task_id 仅支持在24小时内查询任务结果，超时会被系统自动清除。

响应示例
成功响应失败响应
任务数据（如任务状态、图像URL等）仅保留24小时，超时后会被自动清除。请及时保存生成的图片。

 
{
    "request_id": "98d46cd0-1f90-9231-9a6c-xxxxxx",
    "output": {
        "task_id": "15991992-1487-40d4-ae66-xxxxxx",
        "task_status": "SUCCEEDED",
        "submit_time": "2025-06-30 14:37:53.838",
        "scheduled_time": "2025-06-30 14:37:53.858",
        "end_time": "2025-06-30 14:38:11.472",
        "image_url": "http://dashscope-result-hz.oss-cn-hangzhou.aliyuncs.com/tryon.jpg?Expires=xxx"
    },
    "usage": {
        "image_count": 1
    }
}
错误码
大模型服务通用状态码请查阅：错误信息。

AI试衣模型特定错误码如下：

HTTP返回码

错误码（code）

错误信息（message）

含义说明

HTTP返回码

错误码（code）

错误信息（message）

含义说明

400

InvalidParameter

The request is missing required parameters or in a wrong format, please check the parameters that you send.

请求参数缺失或格式错误。请检查您的请求体是否符合API规范。

400

InvalidParameter

Download the media resource timed out during the data inspection process.

图片下载超时。 可能的原因及解决方法如下：

网络问题：您的服务器可能与阿里云百炼服务之间的网络不通。请检查网络链接。

OSS内网URL：阿里云百炼服务无法访问内网地址。请改用OSS公网 URL。

非中国内地资源：跨境网络访问不稳定。请使用中国内地的存储服务。

400

InvalidURL

The request URL is invalid, please check the request URL is available and the request image format is one of the following types: JPEG, JPG, PNG, BMP, and WEBP.

图片URL无效。请检查URL是否为公网地址或者图片格式是否符合要求。

400

InvalidPerson

The input image has no human body or multi human bodies. Please upload other image with single person.

模特图不合规。请确保输入图片中有且仅有一个完整的人。

400

InvalidGarment

Missing clothing image.Please input at least one top garment or bottom garment image.

缺少服饰图片。请至少提供一张上装 (top_garment_url) 或下装 (bottom_garment_url) 的图片。

400

InvalidInputLength

The image resolution is invalid, please make sure that the largest length of image is smaller than 4096, and the smallest length of image is larger than 150. and the size of image ranges from 5KB to 5MB.

图片尺寸或文件大小不符合要求。请参见输入图片要求。