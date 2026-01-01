#!/bin/bash

echo "开始..."
echo "    开始克隆 AndroidModule-AuCommon 仓库..."
git clone https://github.com/jzlhll/AndroidModule-AuCommon.git
echo "    克隆完成，重命名目录..."
mv AndroidModule-AuCommon Module-AndroidCommon
echo "    目录重命名完成"

echo "------------"

echo "    开始克隆 AndroidModule-AuSimplePermission 仓库..."
git clone https://github.com/jzlhll/AndroidModule-AuSimplePermission.git
echo "    克隆完成，重命名目录..."
mv AndroidModule-AuSimplePermission Module-AuSimplePermission
echo "    目录重命名完成"
echo "------------"

echo "    开始添加.git/info/exclude..."
echo "Module-AuSimplePermission/" >> .git/info/exclude
echo "Module-AndroidCommon/" >> .git/info/exclude
echo "    开始添加.git/info/exclude...完成"

echo "结束!"