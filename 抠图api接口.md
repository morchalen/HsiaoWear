真实的Access Key:
63f447c4d33f4391b4be097d6fcea323
Secret Key:
8e4c09bfc53a44febd97e639562f4514



调用 URL
正式环境：
调用方法
Get

签名验证
开放平台接口签名

权限
使用前需提前申请 APPKEY 和 SECRETID。

请求参数
是否必选	参数名	类型	参数说明
必选	task_id	string	任务ID
可选	context	string	客户端上下文信息 格式 v1:base64(data)，v1 为版本号，v1 定义为json格式。
输入值示例
?task_id=6f6b5240-3da3-4299-8222-00aa69a907d7
返回值说明
成功返回值说明
字段	类型	参数说明
data	Object	返回的内容
message	String	错误信息
code	Number	错误代码
data 中单个元素的字段说明

字段	类型	参数说明
status	Number	状态码，-1没有找到任务，0任务创建成功，1 任务执行中，2任务失败，10任务成功。
msg	String	多媒体文件的相关属性信息描述
result	Object	结果
progress	Number	任务进度，例如：0.1 、0.85、1。
result 中单个元素的字段说明

字段	类型	参数说明
id	String	任务ID
urls	String[]	图片地址
失败返回值说明
字段	类型	说明
data	String	返回的内容
message	String	错误信息
code	Int	错误代码
返回值示例
请求成功返回示例
Response Status：200

content-type 为 application/json; charset=utf-8

{
    "code": 0,
    "message":"",
    "data": {
        "status": 10,
        "result": {
            "id": "50309bd5-a827-4125-bc96-62039c93770b",
            "urls": ["https: //obs.mtlab.meitu.com/mtopen/rF5GIhp5ReLKgLV91CKj5BO1q2FTLMmc/MTY5Mjg1MzIwMA==/1c39ef92-04f1-40b4-5b7e-8fcfca0c11ea.png"]
        },
        "progress": 1
    }
}
请求失败返回示例
Response Status：400

content-type 为 application/json; charset=utf-8

{
    "code": 20008, 
    "message": "UNSUITABLE_IMAGE", 
    "data": null
}


本接口用于云端进行显著性检测的请求，能够自动识别图像中的显著性目标区域。支持人像、商品、图形三种类型的显著性检测，并可返回检测结果图或 mask 图，支持边缘细节优化等高级功能。

版本
1.0

图片要求
图片格式：JPG、PNG、HEIC
图片像素尺寸：最小 224×224 像素，最大 3000×3000 像素
图片文件大小：建议控制在3M 内
调用 URL
正式环境：https://openapi.meitu.com
任务提交接口：https://openapi.meitu.com/api/v1/sdk/sync/push
任务名称(task)：/v1/photo_scissors/sod
任务类型(task_type)：mtlab
调用方法
POST

Content-Type: application/json

权限
开放平台接口签名

请求参数
是否必选	参数名	类型	参数说明
必选	params	string	算法参数（JSON 字符串）
必选	init_images	object[]	多媒体文件列表
必选	task	string	固定 /v1/photo_scissors/sod
必选	task_type	string	mtlab
可选	sync_timeout	int	默认 30，同步超时时间
可选	rsp_media_type	string	默认 "url"，返回结果类型,"jpg" 表示 base64
init_images 多媒体文件参数，结构说明如下

是否必选	参数名	类型	参数说明
必选	url	string	多媒体的 url 地址，或者 base64
必选	profile	object	属性信息描述
profile 属性信息描述

是否必选	参数名	类型	参数说明
必选	media_profiles	object	媒体属性信息
必选	version	string	版本信息，固定 v1
media_profiles 媒体属性信息

是否必选	参数名	类型	参数说明
必选	media_data_type	string	媒体数据类型，"url": 表示 url，"jpg": 表示 base64
params 推理参数是 JSON 字符串，其结构说明如下

是否必选	字段	类型	说明
必选	parameter	object	算法核心参数对象
parameter 算法参数详情

是否必选	参数名	类型	说明
可选	nMask	bool	是否返回 mask 图，True 只返回 mask 图，False 返回结果图
可选	model_type	int	选择要使用的抠图模型
0：人像抠图
1：商品抠图
2：图形抠图
若不传，模型内部会自动判断选择使用哪个模型
可选	userboxes	string	图标类型可以添加用户交互框参数，坐标应当使用相对坐标，务必按照左上角开始顺时针的顺序传入四个点的坐标（即左上、右上、右下、左下）。示例：[[[0.01, 0.814], [0.12, 0.814], [0.12, 0.96], [0.01, 0.96]]]
可选	blackwhite	bool	是否返回黑白图，True 只返回黑白 mask 图，False 返回四通道 Mask 图，默认为 False
可选	nbox	bool	是否返回目标位置，True 返回目标位置 top_x, top_y, bottom_x, bottom_y，默认为 False
可选	post_matting	bool	仅针对商品类别，是否使用边缘后处理算法，默认为 False。若设置为 True，会得到更精细的边缘细节，但是耗时会增加 0.2s~1s
可选	use_fe_rgba	bool	控制是否使用前景估计，默认为False。如果图像抠图结果边缘存在白边或黑边等问题，则设置为True，使用前景估计
输入值示例
{
  "task": "/v1/photo_scissors/sod",
  "task_type": "mtlab",
  "params": "{\"parameter\":{\"nMask\":false,\"model_type\":0}}",
  "init_images": [
    {
      "url": "https://example.com/image.jpg",
      "profile": {
        "media_profiles": {
          "media_data_type": "url"
        },
        "version": "v1"
      }
    }
  ],
  "sync_timeout": 30,
  "rsp_media_type": "url"
}
返回值说明
注意，生成的结果，会定期清理，请及时下载保存

字段	类型	说明
request_id	string	请求的标识
code	int	业务状态码
message	string	业务信息
data	object	结果数据
data 中单个元素的字段说明

字段	类型	说明
status	int	任务状态（状态码，-1 没有找到任务，0 任务创建成功，1 任务执行中，2 任务失败， 9 任务超时：使用查询接口 接口查询, 10 任务成功）
result	object	结果数据
task_id	string	任务ID
trace_id	string	链路追踪ID
create_time	int	创建时间
progress	int	进度百分比
predict_elapsed	int	预估耗时
result 中单个元素的字段说明

字段	类型	说明
parameter	object	算法参数信息
media_info_list	array	媒体文件列表
msg	string	状态消息
msg_id	string	消息ID
code	int	算法状态码
error_code	int	错误代码
error_msg	string	错误信息
data	object	算法返回数据
返回值示例
请求成功返回示例
Response Status：200

content-type 为 application/json; charset=utf-8

{
  "request_id": "req_123456789",
  "trace_id": "trace_987654321",
  "code": 0,
  "error_code": 0,
  "message": "success",
  "data": {
    "status": 9,
    "result": {
      "parameter": {
        "version": "1.0",
        "exist_salient": true,
        "Kind": 0
      },
      "media_info_list": [
        {
          "media_data": "https://example.com/result.jpg",
          "media_profiles": {
            "media_data_type": "url"
          }
        }
      ],
      "msg": "success",
      "msg_id": "msg_123456",
      "code": 0,
      "error_code": 0,
      "error_msg": ""
    },
    "progress": 100,
    "predict_elapsed": 5000,
    "create_time": 1640995200000,
    "task_id": "task_1234567890",
    "trace_id": "trace_987654321",
    "client_info": "",
    "init_images": null
  }
}
需要查询返回示例
使用查询接口 接口查询

Response Status：200

content-type 为 application/json; charset=utf-8

{
  "request_id": "",
  "trace_id": "",
  "code": 0,
  "error_code": 0,
  "message": "success",
  "data": {
    "status": 9,
    "result": {
      "id": "t_mt1a3i5n7b3da6d589-46b5-4f66-a0bb-8dd22f2a172e"
    },
    "progress": 0,
    "predict_elapsed": 10000,
    "create_time": 1759202368761,
    "task_id": "t_mt1a3i5n7b3da6d589-46b5-4f66-a0bb-8dd22f2a172e",
    "custom_task_id": "",
    "trace_id": "9129a3a2-99c4-46ce-8731-0e100e2fbee7",
    "client_info": "",
    "init_images": null
  }
}
请求失败返回示例
Response Status：400

content-type 为 application/json; charset=utf-8

{
  "request_id": "req_error_123456",
  "trace_id": "trace_error_789012",
  "code": 20003,
  "error_code": 20003,
  "message": "ALGO_ERROR",
  "tips": null,
  "data": {
    "status": 2,
    "result": {
      "id": "t_mt1a3i5n7be8d575cc-2ffb-4e0c-85a8-1824110e31b8",
      "code": 20003,
      "data": {
        "duration": {
          "alg_process_time": 0,
          "created_timestamp": 1759201989,
          "pull_timestamp": 1759201989,
          "repost_time": 0,
          "upload_time": 0,
          "waiting_time": 0
        },
        "error_code": 20003,
        "error_msg": "DETECT_NOT_FACE",
        "extra": {},
        "media_info_list": [],
        "msg_id": "c1b09cb2-6e05-4d21-55ab-r007b1f21bec",
        "parameter": null
      },
      "msg": "DETECT_NOT_FACE",
      "msg_id": "c1b09cb2-6e05-4d21-55ab-r007b1f21bec"
    },
    "progress": 1,
    "predict_elapsed": 10000,
    "create_time": 1759201989444,
    "task_id": "t_mt1a3i5n7be8d575cc-2ffb-4e0c-85a8-1824110e31b8",
    "custom_task_id": "",
    "trace_id": "",
    "client_info": "",
    "init_images": null
  }
}调用示例（Java）
package com.meitu.openai.common;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import com.meitu.openai.common.Signer;

public class Main {
    public static void main(String[] args) throws Exception {
        Signer signer = new Signer("your api_key","api_secret");

        String url = "http://openapi.meitu.com/api/v1/sdk/sync/push";
        String method = "POST";
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put(Signer.HeaderHost, "openapi.meitu.com");
        String body = "{\n" +
                "  \"task\": \"/v1/photo_scissors/sod\",\n" +
                "  \"task_type\": \"mtlab\",\n" +
                "  \"params\": \"{\\\"parameter\\\":{\\\"nMask\\\":false,\\\"model_type\\\":0}}\",\n" +
                "  \"init_images\": [\n" +
                "    {\n" +
                "      \"url\": \"https://example.com/image.jpg\",\n" +
                "      \"profile\": {\n" +
                "        \"media_profiles\": {\n" +
                "          \"media_data_type\": \"url\"\n" +
                "        },\n" +
                "        \"version\": \"v1\"\n" +
                "      }\n" +
                "    }\n" +
                "  ],\n" +
                "  \"sync_timeout\": 30,\n" +
                "  \"rsp_media_type\": \"url\"\n" +
                "}";
        Map<String,String> signedHeaders = signer.sign(url, method, headers, body);
        System.out.println("signedHeader: "+signedHeaders);
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            for (Map.Entry<String,String> entry : signedHeaders.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            connection.setDoOutput(true);
            if (body!=null){
                connection.getOutputStream().write(body.getBytes());
            }
            int status = connection.getResponseCode();
            System.out.println(status);

            InputStream inputStream;
            if (status >= 400) {
                inputStream = connection.getErrorStream();
            } else {
                inputStream = connection.getInputStream();
            }
            BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            System.out.println("======");
            System.out.println(response.toString());
            System.out.println("======");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }
}